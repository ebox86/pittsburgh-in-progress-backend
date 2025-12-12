package com.pip.pdffetch.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pip.pdffetch.config.PdfFetchConfig;
import com.pip.pdffetch.model.DocumentDiscoveryRequest;
import com.pip.pdffetch.pubsub.PubSubMessage;
import com.pip.pdffetch.pubsub.PubSubPushEnvelope;
import com.pip.pdffetch.service.PdfFetchService;
import com.pip.pdffetch.service.PdfStoredPublisherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.Map;

@RestController
public class PubSubPushController {

    private static final Logger log = LoggerFactory.getLogger(PubSubPushController.class);

    private final ObjectMapper objectMapper;
    private final PdfFetchService pdfFetchService;
    private final PdfStoredPublisherService pdfStoredPublisherService;
    private final PdfFetchConfig pdfFetchConfig;

    public PubSubPushController(ObjectMapper objectMapper,
                                PdfFetchService pdfFetchService,
                                PdfStoredPublisherService pdfStoredPublisherService,
                                PdfFetchConfig pdfFetchConfig) {
        this.objectMapper = objectMapper;
        this.pdfFetchService = pdfFetchService;
        this.pdfStoredPublisherService = pdfStoredPublisherService;
        this.pdfFetchConfig = pdfFetchConfig;
    }

    @PostMapping("/")
    public ResponseEntity<Void> handlePubSubPush(@RequestBody PubSubPushEnvelope envelope) {
        try {
            PubSubMessage message = envelope == null ? null : envelope.getMessage();
            if (envelope == null || message == null || message.getData() == null) {
                log.warn("Received Pub/Sub push without data");
                return ResponseEntity.badRequest().build();
            }

            String messageContext = describePubSubMessage(message);
            byte[] decoded = Base64.getDecoder().decode(message.getData());
            DocumentDiscoveryRequest request = objectMapper.readValue(decoded, DocumentDiscoveryRequest.class);
            log.info("Received document discovery request for {} from {}", request.getMeetingId(), request.getMeetingUrl());

            PdfFetchService.FetchResult fetchResult;
            try {
                fetchResult = pdfFetchService.fetchAndStorePdf(request);
            } catch (IllegalStateException e) {
                if (isClientErrorStatus(e.getMessage())) {
                    // Treat 4xx errors as non-retryable to avoid hammering the city’s server.
                    log.warn("Non-retryable fetch failure for {} {}: {}", request.getMeetingUrl(), messageContext, e.getMessage());
                    return ResponseEntity.ok().build();
                }
                throw e;
            }

            if (fetchResult == null) {
                log.warn("Skipping publish for {} at {} ({}): fetch returned non-retryable status", request.getMeetingId(), request.getMeetingUrl(), messageContext);
                return ResponseEntity.ok().build();
            }
            pdfStoredPublisherService.publishPdfStored(
                    request,
                    pdfFetchConfig.getBucket(),
                    fetchResult.objectName(),
                    fetchResult.contentType(),
                    fetchResult.sizeBytes());

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to process Pub/Sub push", e);
            return ResponseEntity.status(500).build();
        }
    }

    private static boolean isClientErrorStatus(String message) {
        if (message == null) {
            return false;
        }
        int index = message.indexOf("status ");
        if (index == -1) {
            return false;
        }
        String after = message.substring(index + 7);
        int space = after.indexOf(' ');
        String code = space == -1 ? after : after.substring(0, space);
        try {
            int status = Integer.parseInt(code);
            return status >= 400 && status < 500;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static String describePubSubMessage(PubSubMessage message) {
        if (message == null) {
            return "messageId=<unknown> attributes={}";
        }
        String messageId = message.getMessageId() == null ? "<unknown>" : message.getMessageId();
        Map<String, String> attrs = message.getAttributes();
        return "messageId=" + messageId + " attributes=" + (attrs == null ? "{}" : attrs);
    }
}
