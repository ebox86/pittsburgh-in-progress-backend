package com.pip.projectlink.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pip.ingest.model.ProjectCandidateEvent;
import com.pip.ingest.pubsub.PubSubMessage;
import com.pip.ingest.pubsub.PubSubPushEnvelope;
import com.pip.projectlink.service.LinkOutcome;
import com.pip.projectlink.service.ProjectLinkResult;
import com.pip.projectlink.service.ProjectLinkService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PubSubPushControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProjectLinkService projectLinkService;

    @Test
    void handlePubSubPush_delegatesToService() throws Exception {
        ProjectCandidateEvent event = new ProjectCandidateEvent(
                "project-candidate",
                "meeting-1",
                "planning",
                "2025-11-01",
                "https://example.com/original.pdf",
                "test-bucket",
                "document.pdf",
                1,
                123L,
                "text");
        byte[] payload = objectMapper.writeValueAsBytes(event);
        String base64 = Base64.getEncoder().encodeToString(payload);

        PubSubMessage message = new PubSubMessage();
        message.setData(base64);
        message.setMessageId("msg-1");
        Map<String, String> attrs = new HashMap<>();
        attrs.put("source", "tests");
        message.setAttributes(attrs);

        PubSubPushEnvelope envelope = new PubSubPushEnvelope();
        envelope.setMessage(message);
        envelope.setSubscription("pgh-project-candidate-push");

        when(projectLinkService.linkProject(any()))
                .thenReturn(new ProjectLinkResult("meeting-1", LinkOutcome.CREATED));

        mockMvc.perform(post("/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(envelope)))
                .andExpect(status().isOk());

        ArgumentCaptor<ProjectCandidateEvent> captor = ArgumentCaptor.forClass(ProjectCandidateEvent.class);
        verify(projectLinkService).linkProject(captor.capture());
        ProjectCandidateEvent captured = captor.getValue();
        assertEquals("meeting-1", captured.meetingId());
        assertEquals("document.pdf", captured.objectName());
    }
}
