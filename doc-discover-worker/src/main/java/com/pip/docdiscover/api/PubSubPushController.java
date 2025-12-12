package com.pip.docdiscover.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pip.docdiscover.model.MeetingDiscoveredEvent;
import com.pip.docdiscover.pubsub.PubSubPushEnvelope;
import com.pip.docdiscover.service.DocumentDiscoverPublisherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@RestController
public class PubSubPushController {

    private static final Logger log = LoggerFactory.getLogger(PubSubPushController.class);

    private final DocumentDiscoverPublisherService publisherService;
    private final ObjectMapper objectMapper;

    public PubSubPushController(DocumentDiscoverPublisherService publisherService, ObjectMapper objectMapper) {
        this.publisherService = publisherService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/")
    public ResponseEntity<Void> handlePubSubPush(@RequestBody PubSubPushEnvelope envelope) {
        try {
            String data = envelope.getMessage().getData();
            byte[] decoded = Base64.getDecoder().decode(data);
            String json = new String(decoded, StandardCharsets.UTF_8);
            MeetingDiscoveredEvent meeting = objectMapper.readValue(json, MeetingDiscoveredEvent.class);
            log.info("Received meeting {} at {}", meeting.getMeetingId(), meeting.getMeetingUrl());
            publisherService.publishDocumentDiscovery(meeting);
            return ResponseEntity.ok().build();
        } catch (Exception ex) {
            log.error("Failed to process Pub/Sub push", ex);
            return ResponseEntity.status(500).build();
        }
    }
}
