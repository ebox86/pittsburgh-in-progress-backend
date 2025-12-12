package com.pip.pdfparse.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.core.ApiFuture;
import com.google.protobuf.ByteString;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.pubsub.v1.PubsubMessage;
import com.pip.pdfparse.model.PdfParsedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ProjectCandidatePublisherService {

    private static final Logger log = LoggerFactory.getLogger(ProjectCandidatePublisherService.class);

    private final Publisher publisher;
    private final ObjectMapper objectMapper;

    public ProjectCandidatePublisherService(Publisher publisher, ObjectMapper objectMapper) {
        this.publisher = publisher;
        this.objectMapper = objectMapper;
    }

    public void publishProjectCandidate(PdfParsedEvent event) {
        byte[] payload;
        try {
            payload = objectMapper.writeValueAsBytes(event);
        } catch (JsonProcessingException e) {
            log.error("Unable to serialize project candidate event for {}", event.meetingId(), e);
            throw new IllegalStateException("Unable to serialize project candidate event", e);
        }

        PubsubMessage message = PubsubMessage.newBuilder()
                .setData(ByteString.copyFrom(payload))
                .build();

        try {
            ApiFuture<String> future = publisher.publish(message);
            String messageId = future.get();
            log.info("Published project-candidate event for {} as {}", event.meetingId(), messageId);
        } catch (Exception e) {
            log.error("Failed to publish project-candidate for {}", event.meetingId(), e);
            throw new IllegalStateException("Unable to publish project-candidate event", e);
        }
    }
}
