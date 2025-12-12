package com.pip.ingest.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.core.ApiFuture;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;

@Service
public class MeetingsPublisherService {

    private static final Logger log = LoggerFactory.getLogger(MeetingsPublisherService.class);

    private final Publisher publisher;
    private final ObjectMapper objectMapper;

    public MeetingsPublisherService(Publisher publisher, ObjectMapper objectMapper) {
        this.publisher = publisher;
        this.objectMapper = objectMapper;
    }

    public void publishFakeMeeting() {
        Meeting meeting = new Meeting(
                "PC-2025-11-18",
                "https://pittsburghpa.gov/planning/meetings/planning-commission",
                "planning-commission",
                "2025-11-18T18:00:00Z"
        );

        String payload;
        try {
            payload = objectMapper.writeValueAsString(meeting);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize fake meeting", e);
        }

        PubsubMessage message = PubsubMessage.newBuilder()
                .setData(ByteString.copyFromUtf8(payload))
                .build();

        try {
            ApiFuture<String> future = publisher.publish(message);
            String messageId = future.get();
            log.info("Published fake meeting {} as message {}", meeting.meetingId(), messageId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Meeting publish interrupted", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to publish fake meeting", e.getCause());
        }
    }

    private record Meeting(String meetingId, String meetingUrl, String meetingType, String meetingDate) {
    }
}
