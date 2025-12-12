package com.pip.docdiscover.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pip.docdiscover.model.MeetingDiscoveredEvent;
import com.pip.docdiscover.pubsub.PubSubMessage;
import com.pip.docdiscover.pubsub.PubSubPushEnvelope;
import com.pip.docdiscover.service.DocumentDiscoverPublisherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

class PubSubPushControllerTest {

    private DocumentDiscoverPublisherService publisherService;
    private ObjectMapper objectMapper;
    private PubSubPushController controller;

    @BeforeEach
    void setUp() {
        publisherService = Mockito.mock(DocumentDiscoverPublisherService.class);
        objectMapper = new ObjectMapper();
        controller = new PubSubPushController(publisherService, objectMapper);
    }

    @Test
    void handlePubSubPushPublishesDecodedMeeting() throws Exception {
        MeetingDiscoveredEvent meeting = new MeetingDiscoveredEvent();
        meeting.setMeetingId("PC-2025-11-18");
        meeting.setMeetingUrl("https://example.com/meeting");
        meeting.setMeetingType("planning-commission");
        meeting.setMeetingDate("2025-11-18");

        String meetingJson = objectMapper.writeValueAsString(meeting);
        String encoded = Base64.getEncoder().encodeToString(meetingJson.getBytes(StandardCharsets.UTF_8));

        PubSubMessage message = new PubSubMessage();
        message.setData(encoded);
        PubSubPushEnvelope envelope = new PubSubPushEnvelope();
        envelope.setMessage(message);
        envelope.setSubscription("projects/test/subscriptions/sub1");

        ResponseEntity<Void> response = controller.handlePubSubPush(envelope);

        assertThat(response.getStatusCodeValue()).isEqualTo(200);

        ArgumentCaptor<MeetingDiscoveredEvent> captor = ArgumentCaptor.forClass(MeetingDiscoveredEvent.class);
        verify(publisherService).publishDocumentDiscovery(captor.capture());
        assertThat(captor.getValue().getMeetingId()).isEqualTo(meeting.getMeetingId());
        assertThat(captor.getValue().getMeetingUrl()).isEqualTo(meeting.getMeetingUrl());
    }
}
