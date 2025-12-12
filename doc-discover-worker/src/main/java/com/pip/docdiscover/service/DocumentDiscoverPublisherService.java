package com.pip.docdiscover.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.pip.docdiscover.model.MeetingDiscoveredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class DocumentDiscoverPublisherService {

    private static final Logger log = LoggerFactory.getLogger(DocumentDiscoverPublisherService.class);

    private final Publisher documentPublisher;
    private final ObjectMapper objectMapper;

    public DocumentDiscoverPublisherService(Publisher documentPublisher, ObjectMapper objectMapper) {
        this.documentPublisher = documentPublisher;
        this.objectMapper = objectMapper;
    }

    public void publishDocumentDiscovery(MeetingDiscoveredEvent meeting) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "document-discovery-request");
        payload.put("meetingId", meeting.getMeetingId());
        payload.put("meetingUrl", meeting.getMeetingUrl());
        payload.put("meetingType", meeting.getMeetingType());
        payload.put("meetingDate", meeting.getMeetingDate());

        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            PubsubMessage pubsubMessage = PubsubMessage.newBuilder()
                    .setData(ByteString.copyFromUtf8(payloadJson))
                    .build();
            documentPublisher.publish(pubsubMessage).get();
            log.info("Published document discovery request for {}", meeting.getMeetingId());
        } catch (Exception ex) {
            log.error("Failed to publish document discovery request for {}", meeting.getMeetingId(), ex);
            throw new IllegalStateException("Unable to publish document discovery request", ex);
        }
    }
}
