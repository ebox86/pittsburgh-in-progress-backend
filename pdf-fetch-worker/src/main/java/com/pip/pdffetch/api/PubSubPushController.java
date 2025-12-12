package com.pip.pdffetch.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pip.pdffetch.config.PdfFetchConfig;
import com.pip.pdffetch.model.DocumentDiscoveryRequest;
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
            if (envelope == null || envelope.getMessage() == null || envelope.getMessage().getData() == null) {
                log.warn("Received Pub/Sub push without data");
                return ResponseEntity.badRequest().build();
            }

            byte[] decoded = Base64.getDecoder().decode(envelope.getMessage().getData());
            DocumentDiscoveryRequest request = objectMapper.readValue(decoded, DocumentDiscoveryRequest.class);
            log.info("Received document discovery request for {} from {}", request.getMeetingId(), request.getMeetingUrl());

            PdfFetchService.FetchResult fetchResult = pdfFetchService.fetchAndStorePdf(request);
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
}
