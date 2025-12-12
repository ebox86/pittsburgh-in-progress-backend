package com.pip.pdffetch.service;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.pip.pdffetch.model.DocumentDiscoveryRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PdfFetchService {

    private static final Logger log = LoggerFactory.getLogger(PdfFetchService.class);

    private final Storage storage;
    private final String bucket;
    private final HttpClient httpClient;

    @Autowired
    public PdfFetchService(Storage storage, @Value("${pip.pdf-bucket}") String bucket) {
        this(storage, bucket, createDefaultHttpClient());
    }

    PdfFetchService(Storage storage, String bucket, HttpClient httpClient) {
        this.storage = storage;
        this.bucket = bucket;
        this.httpClient = httpClient;
    }

    private static HttpClient createDefaultHttpClient() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    public record FetchResult(String objectName, String contentType, long sizeBytes) {
    }

    public FetchResult fetchAndStorePdf(DocumentDiscoveryRequest request) {
        if (request == null || request.getMeetingUrl() == null) {
            throw new IllegalArgumentException("DocumentDiscoveryRequest and meetingUrl must be provided");
        }

        String meetingId = Optional.ofNullable(request.getMeetingId())
                .filter(id -> !id.isBlank())
                .orElse("unknown");
        String objectName = "meetings/" + meetingId + ".pdf";
        BlobId blobId = BlobId.of(bucket, objectName);
        Blob existingBlob = storage.get(blobId);
        if (existingBlob != null) {
            log.info("Skipping fetch for {} because gs://{}/{} already exists", request.getMeetingUrl(), bucket, objectName);
            return new FetchResult(objectName, existingBlob.getContentType(), existingBlob.getSize());
        }

        URI pdfUri = URI.create(request.getMeetingUrl());
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(pdfUri)
                .GET()
                .header("User-Agent", "curl/8.5.0")
                .header("Accept", "*/*")
                .build();

        HttpResponse<byte[]> response;
        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while downloading PDF", e);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to download PDF", e);
        }

        byte[] body = response.body();
        int statusCode = response.statusCode();

        if (statusCode >= 200 && statusCode < 300) {
            String contentType = response.headers()
                    .firstValue("content-type")
                    .orElse("application/octet-stream");

            if (!contentType.toLowerCase().contains("application/pdf")) {
                log.warn("PDF response for {} returned non-PDF content type {}", request.getMeetingId(), contentType);
            }

            long sizeBytes = body.length;
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType(contentType)
                    .build();

            Blob blob = storage.create(blobInfo, body);
            log.info("Stored PDF for {} into gs://{}/{} ({} bytes)", meetingId, bucket, objectName, sizeBytes);

            return new FetchResult(objectName, blob.getContentType(), blob.getSize());
        }

        Map<String, List<String>> headers = response.headers().map();
        String snippet = extractResponseSnippet(body);

        if (statusCode == 403 || statusCode == 404) {
            log.warn("Non-retryable status while fetching PDF from {}: {} - headers {}, bodySnippet='{}'",
                    request.getMeetingUrl(), statusCode, headers, snippet);
            return null;
        }

        log.warn("Failed to fetch PDF from {} - status {}, headers {}, bodySnippet='{}'",
                request.getMeetingUrl(), statusCode, headers, snippet);
        throw new IllegalStateException("Unexpected status " + statusCode
                + " while fetching PDF from " + request.getMeetingUrl());
    }

    private static String extractResponseSnippet(byte[] body) {
        if (body == null || body.length == 0) {
            return "";
        }
        int length = Math.min(body.length, 512);
        return new String(body, 0, length, StandardCharsets.UTF_8);
    }
}
