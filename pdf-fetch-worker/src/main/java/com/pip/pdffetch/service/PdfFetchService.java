package com.pip.pdffetch.service;

import com.google.cloud.WriteChannel;
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
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
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

    public PdfFetchOutcome fetchAndStorePdf(DocumentDiscoveryRequest request) {
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
            log.info("Reusing existing PDF for {} at gs://{}/{} ({} bytes)",
                    meetingId, bucket, objectName, existingBlob.getSize());
            return new PdfFetchOutcome(existingBlob, PdfFetchStatus.REUSED);
        }

        URI pdfUri = URI.create(request.getMeetingUrl());
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(pdfUri)
                .GET()
                .header("User-Agent", "curl/8.5.0")
                .header("Accept", "*/*")
                .build();

        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while downloading PDF", e);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to download PDF", e);
        }

        int statusCode = response.statusCode();

        if (statusCode >= 200 && statusCode < 300) {
            String contentType = response.headers()
                    .firstValue("content-type")
                    .orElse("application/octet-stream");

            if (!contentType.toLowerCase().contains("application/pdf")) {
                log.warn("PDF response for {} returned non-PDF content type {}", request.getMeetingId(), contentType);
            }

            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType(contentType)
                    .build();
            try (InputStream bodyStream = response.body()) {
                streamToStorage(bodyStream, blobInfo);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to download PDF", e);
            }

            Blob blob = storage.get(blobId);
            if (blob == null) {
                throw new IllegalStateException("Failed to verify uploaded PDF for " + objectName);
            }
            log.info("Stored new PDF for {} into gs://{}/{} ({} bytes)",
                    meetingId, bucket, objectName, blob.getSize());

            return new PdfFetchOutcome(blob, PdfFetchStatus.CREATED);
        }

        Map<String, List<String>> headers = response.headers().map();
        String snippet;
        try (InputStream bodyStream = response.body()) {
            snippet = readSnippet(bodyStream);
        } catch (IOException e) {
            log.warn("Unable to read error response snippet for {}", request.getMeetingUrl(), e);
            snippet = "";
        }

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


    private long streamToStorage(InputStream bodyStream, BlobInfo blobInfo) throws IOException {
        try (ReadableByteChannel source = Channels.newChannel(bodyStream);
             WriteChannel writer = storage.writer(blobInfo)) {
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            long total = 0;
            while (source.read(buffer) != -1) {
                buffer.flip();
                while (buffer.hasRemaining()) {
                    total += writer.write(buffer);
                }
                buffer.clear();
            }
            return total;
        }
    }

    private static String readSnippet(InputStream stream) {
        byte[] buffer = new byte[512];
        int totalRead = 0;
        try {
            while (totalRead < buffer.length) {
                int read = stream.read(buffer, totalRead, buffer.length - totalRead);
                if (read == -1) {
                    break;
                }
                totalRead += read;
            }
        } catch (IOException e) {
            log.warn("Unable to read snippet from response", e);
            return "";
        }
        if (totalRead == 0) {
            return "";
        }
        return new String(buffer, 0, totalRead, StandardCharsets.UTF_8);
    }
}
