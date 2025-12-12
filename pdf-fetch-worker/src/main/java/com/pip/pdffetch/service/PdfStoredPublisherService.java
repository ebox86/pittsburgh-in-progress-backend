package com.pip.pdffetch.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.core.ApiFuture;
import com.google.protobuf.ByteString;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.pubsub.v1.PubsubMessage;
import com.pip.pdffetch.model.DocumentDiscoveryRequest;
import com.pip.pdffetch.model.PdfStoredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PdfStoredPublisherService {

    private static final Logger log = LoggerFactory.getLogger(PdfStoredPublisherService.class);

    private final Publisher pdfStoredPublisher;
    private final ObjectMapper objectMapper;

    public PdfStoredPublisherService(Publisher pdfStoredPublisher, ObjectMapper objectMapper) {
        this.pdfStoredPublisher = pdfStoredPublisher;
        this.objectMapper = objectMapper;
    }

    public void publishPdfStored(DocumentDiscoveryRequest request,
                                 String bucket,
                                 String objectName,
                                 String contentType,
                                 long sizeBytes) {
        PdfStoredEvent event = new PdfStoredEvent();
        event.setType("pdf-stored");
        event.setMeetingId(request.getMeetingId());
        event.setSourceUrl(request.getMeetingUrl());
        event.setBucket(bucket);
        event.setObjectName(objectName);
        event.setContentType(contentType);
        event.setSizeBytes(sizeBytes);

        byte[] payload;
        try {
            payload = objectMapper.writeValueAsBytes(event);
        } catch (JsonProcessingException e) {
            log.error("Unable to serialize pdf-stored event for {}", request.getMeetingId(), e);
            throw new IllegalStateException("Unable to serialize pdf-stored event", e);
        }

        PubsubMessage message = PubsubMessage.newBuilder()
                .setData(ByteString.copyFrom(payload))
                .build();
        try {
            ApiFuture<String> future = pdfStoredPublisher.publish(message);
            String messageId = future.get();
            log.info("Published pdf-stored event for {} as {}", request.getMeetingId(), messageId);
        } catch (Exception e) {
            log.error("Failed to publish pdf-stored for {}", request.getMeetingId(), e);
            throw new IllegalStateException("Unable to publish pdf-stored event", e);
        }
    }
}
