package com.pip.pdffetch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.storage.Blob;
import com.pip.pdffetch.api.PubSubPushController;
import com.pip.pdffetch.config.PdfFetchConfig;
import com.pip.pdffetch.model.DocumentDiscoveryRequest;
import com.pip.pdffetch.service.PdfFetchOutcome;
import com.pip.pdffetch.service.PdfFetchService;
import com.pip.pdffetch.service.PdfFetchStatus;
import com.pip.pdffetch.service.PdfStoredPublisherService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PubSubPushController.class)
class PubSubPushControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PdfFetchService pdfFetchService;

    @MockBean
    private PdfStoredPublisherService pdfStoredPublisherService;

    @MockBean
    private PdfFetchConfig pdfFetchConfig;

    @Test
    void handlePubSubPush_happyPath() throws Exception {
        DocumentDiscoveryRequest request = new DocumentDiscoveryRequest();
        request.setType("document-discovery-request");
        request.setMeetingId("PC-2025-11-18");
        request.setMeetingUrl("https://example.com/fake.pdf");
        request.setMeetingType("planning-commission");
        request.setMeetingDate("2025-11-18");

        Blob blob = mock(Blob.class);
        when(blob.getBucket()).thenReturn("pgh-pdfs");
        when(blob.getName()).thenReturn("meetings/PC-2025-11-18.pdf");
        when(blob.getContentType()).thenReturn("application/pdf");
        when(blob.getSize()).thenReturn(2048L);
        PdfFetchOutcome outcome = new PdfFetchOutcome(blob, PdfFetchStatus.CREATED);
        when(pdfFetchService.fetchAndStorePdf(any(DocumentDiscoveryRequest.class))).thenReturn(outcome);
        when(pdfFetchConfig.getBucket()).thenReturn("pgh-pdfs");

        String documentJson = objectMapper.writeValueAsString(request);
        String base64Data = Base64.getEncoder().encodeToString(documentJson.getBytes(StandardCharsets.UTF_8));

        String payload = """
                {
                  "message": {
                    "data": "%s",
                    "messageId": "fake-id-456",
                    "publishTime": "2025-11-18T12:00:00Z"
                  },
                  "subscription": "projects/pittsburgh-in-progress/subscriptions/test-doc-discover"
                }
                """.formatted(base64Data);

        mockMvc.perform(post("/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        ArgumentCaptor<DocumentDiscoveryRequest> requestCaptor = ArgumentCaptor.forClass(DocumentDiscoveryRequest.class);
        verify(pdfStoredPublisherService).publishPdfStored(
                requestCaptor.capture(),
                eq("pgh-pdfs"),
                eq("meetings/PC-2025-11-18.pdf"),
                eq("application/pdf"),
                eq(2048L));

        assertEquals("PC-2025-11-18", requestCaptor.getValue().getMeetingId());
    }

    @Test
    void handlePubSubPush_clientErrorIsAcked() throws Exception {
        DocumentDiscoveryRequest request = new DocumentDiscoveryRequest();
        request.setType("document-discovery-request");
        request.setMeetingId("PC-2025-11-18");
        request.setMeetingUrl("https://example.com/fake.pdf");
        request.setMeetingType("planning-commission");
        request.setMeetingDate("2025-11-18");

        when(pdfFetchService.fetchAndStorePdf(any(DocumentDiscoveryRequest.class)))
                .thenThrow(new IllegalStateException("Unexpected status 403 while fetching PDF from https://example.com/fake.pdf"));
        String documentJson = objectMapper.writeValueAsString(request);
        String base64Data = Base64.getEncoder().encodeToString(documentJson.getBytes(StandardCharsets.UTF_8));
        String payload = """
                {
                  "message": {
                    "data": "%s",
                    "messageId": "fake-id-456",
                    "publishTime": "2025-11-18T12:00:00Z"
                  },
                  "subscription": "projects/pittsburgh-in-progress/subscriptions/test-doc-discover"
                }
                """.formatted(base64Data);

        mockMvc.perform(post("/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        verify(pdfStoredPublisherService, never()).publishPdfStored(any(), any(), any(), any(), anyLong());
    }
}
