package com.pip.pdfparse.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pip.ingest.model.PdfStoredEvent;
import com.pip.pdfparse.service.PdfParseResultStatus;
import com.pip.pdfparse.service.PdfParseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PubSubPushController.class)
class PubSubPushControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PdfParseService pdfParseService;

    @Test
    void handlePubSubPush_parsesAndPublishes() throws Exception {
        PdfStoredEvent event = new PdfStoredEvent();
        event.setMeetingId("meeting-123");
        event.setBucket("test-bucket");
        event.setObjectName("meetings/meeting-123.pdf");
        event.setSourceUrl("https://example.com/test.pdf");
        event.setMeetingType("planning");
        event.setMeetingDate("2025-11-18");

        when(pdfParseService.parseAndPublish(any(PdfStoredEvent.class))).thenReturn(PdfParseResultStatus.PARSED_AND_PUBLISHED);

        String base64Data = Base64.getEncoder().encodeToString(objectMapper.writeValueAsString(event).getBytes(StandardCharsets.UTF_8));
        String payload = """
                {
                  "message": {
                    "data": "%s",
                    "messageId": "msg-1",
                    "publishTime": "2025-12-12T12:00:00Z"
                  },
                  "subscription": "projects/pittsburgh-in-progress/subscriptions/pgh-pdf-stored-push"
                }
                """.formatted(base64Data);

        mockMvc.perform(post("/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        verify(pdfParseService).parseAndPublish(any(PdfStoredEvent.class));
    }
}
