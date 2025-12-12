package com.pip.pdffetch.service;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.pip.pdffetch.model.DocumentDiscoveryRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PdfFetchServiceTest {

    @Mock
    private Storage storage;

    @Mock
    private HttpClient httpClient;

    @Mock
    private Blob blob;

    private PdfFetchService pdfFetchService;

    @BeforeEach
    void setUp() {
        pdfFetchService = new PdfFetchService(storage, "test-bucket", httpClient);
    }

    @Test
    void fetchAndStorePdf_successfulFetchStoresBlob() throws Exception {
        DocumentDiscoveryRequest request = new DocumentDiscoveryRequest();
        request.setMeetingId("meeting-123");
        request.setMeetingUrl("https://example.com/meeting.pdf");

        byte[] pdfContent = new byte[]{1, 2, 3, 4};
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(pdfContent);
        HttpHeaders headers = HttpHeaders.of(Map.of("content-type", List.of("application/pdf")), (k, v) -> true);
        when(response.headers()).thenReturn(headers);

        when(httpClient.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(response);
        when(storage.create(any(BlobInfo.class), eq(pdfContent))).thenReturn(blob);
        when(blob.getContentType()).thenReturn("application/pdf");
        when(blob.getSize()).thenReturn((long) pdfContent.length);

        PdfFetchService.FetchResult result = pdfFetchService.fetchAndStorePdf(request);

        assertEquals("meetings/meeting-123.pdf", result.objectName());
        assertEquals("application/pdf", result.contentType());
        assertEquals(pdfContent.length, result.sizeBytes());

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
        HttpRequest captured = requestCaptor.getValue();
        assertEquals("curl/8.5.0", captured.headers().firstValue("User-Agent").orElse(""));
        assertEquals("*/*", captured.headers().firstValue("Accept").orElse(""));
    }

    @Test
    void fetchAndStorePdf_non200ResponseThrows() throws Exception {
        DocumentDiscoveryRequest request = new DocumentDiscoveryRequest();
        request.setMeetingId("meeting-404");
        request.setMeetingUrl("https://example.com/denied.pdf");

        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(403);
        byte[] body = "forbidden".getBytes(StandardCharsets.UTF_8);
        when(response.body()).thenReturn(body);
        HttpHeaders headers = HttpHeaders.of(Map.of("content-type", List.of("text/plain")), (k, v) -> true);
        when(response.headers()).thenReturn(headers);

        when(httpClient.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(response);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> pdfFetchService.fetchAndStorePdf(request));
        assertEquals("Unexpected status 403 while fetching PDF from https://example.com/denied.pdf", thrown.getMessage());
        verify(storage, never()).create(any(BlobInfo.class), any());
    }
}
