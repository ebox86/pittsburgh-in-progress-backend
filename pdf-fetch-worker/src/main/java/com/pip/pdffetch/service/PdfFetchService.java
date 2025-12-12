package com.pip.pdffetch.service;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.pip.pdffetch.model.DocumentDiscoveryRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

@Service
public class PdfFetchService {

    private static final Logger log = LoggerFactory.getLogger(PdfFetchService.class);

    private final Storage storage;
    private final String bucket;
    private final HttpClient httpClient;

    public PdfFetchService(Storage storage, @Value("${pip.pdf-bucket}") String bucket) {
        this.storage = storage;
        this.bucket = bucket;
        this.httpClient = HttpClient.newBuilder()
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

        URI pdfUri = URI.create(request.getMeetingUrl());
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(pdfUri)
                .GET()
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

        if (response.statusCode() != 200) {
            throw new IllegalStateException("Unexpected status " + response.statusCode()
                    + " while fetching PDF from " + request.getMeetingUrl());
        }

        String contentType = response.headers()
                .firstValue("content-type")
                .orElse("application/octet-stream");

        if (!contentType.toLowerCase().contains("application/pdf")) {
            log.warn("PDF response for {} returned non-PDF content type {}", request.getMeetingId(), contentType);
        }

        byte[] body = response.body();
        long sizeBytes = body.length;
        String meetingId = Optional.ofNullable(request.getMeetingId())
                .filter(id -> !id.isBlank())
                .orElse("unknown");
        String objectName = "meetings/" + meetingId + ".pdf";

        BlobId blobId = BlobId.of(bucket, objectName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType(contentType)
                .build();

        Blob blob = storage.create(blobInfo, body);
        log.info("Stored PDF for {} into gs://{}/{} ({} bytes)", meetingId, bucket, objectName, sizeBytes);

        return new FetchResult(objectName, blob.getContentType(), blob.getSize());
    }
}
