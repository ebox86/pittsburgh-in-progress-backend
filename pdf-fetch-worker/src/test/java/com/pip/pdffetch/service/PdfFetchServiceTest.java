package com.pip.pdffetch.service;

import com.google.cloud.WriteChannel;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(new ByteArrayInputStream(pdfContent));
        HttpHeaders headers = HttpHeaders.of(Map.of("content-type", List.of("application/pdf")), (k, v) -> true);
        when(response.headers()).thenReturn(headers);

        when(httpClient.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<InputStream>>any()))
                .thenReturn(response);

        BlobId blobId = BlobId.of("test-bucket", "meetings/meeting-123.pdf");
        when(storage.get(blobId)).thenReturn(null, blob);

        WriteChannel writer = mock(WriteChannel.class);
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        when(writer.write(any(ByteBuffer.class))).thenAnswer(invocation -> {
            ByteBuffer buffer = invocation.getArgument(0);
            int remaining = buffer.remaining();
            byte[] chunk = new byte[remaining];
            buffer.get(chunk);
            captured.write(chunk);
            return remaining;
        });
        when(storage.writer(any(BlobInfo.class))).thenReturn(writer);

        when(blob.getContentType()).thenReturn("application/pdf");
        when(blob.getSize()).thenReturn((long) pdfContent.length);

        PdfFetchOutcome result = pdfFetchService.fetchAndStorePdf(request);

        assertEquals(PdfFetchStatus.CREATED, result.status());
        assertEquals(blob, result.blob());
        assertArrayEquals(pdfContent, captured.toByteArray());

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), ArgumentMatchers.<HttpResponse.BodyHandler<InputStream>>any());
        HttpRequest capturedRequest = requestCaptor.getValue();
        assertEquals("curl/8.5.0", capturedRequest.headers().firstValue("User-Agent").orElse(""));
        assertEquals("*/*", capturedRequest.headers().firstValue("Accept").orElse(""));
        verify(writer).close();
    }

    @Test
    void fetchAndStorePdf_nonRetryableStatusReturnsNull() throws Exception {
        DocumentDiscoveryRequest request = new DocumentDiscoveryRequest();
        request.setMeetingId("meeting-404");
        request.setMeetingUrl("https://example.com/denied.pdf");

        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(403);
        byte[] body = "forbidden".getBytes(StandardCharsets.UTF_8);
        when(response.body()).thenReturn(new ByteArrayInputStream(body));
        HttpHeaders headers = HttpHeaders.of(Map.of("content-type", List.of("text/plain")), (k, v) -> true);
        when(response.headers()).thenReturn(headers);

        when(httpClient.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<InputStream>>any()))
                .thenReturn(response);

        PdfFetchOutcome result = pdfFetchService.fetchAndStorePdf(request);
        assertNull(result);
        verify(storage, never()).writer(any(BlobInfo.class));
    }

    @Test
    void fetchAndStorePdf_serverErrorThrows() throws Exception {
        DocumentDiscoveryRequest request = new DocumentDiscoveryRequest();
        request.setMeetingId("meeting-500");
        request.setMeetingUrl("https://example.com/error.pdf");

        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(500);
        byte[] body = "server err".getBytes(StandardCharsets.UTF_8);
        when(response.body()).thenReturn(new ByteArrayInputStream(body));
        HttpHeaders headers = HttpHeaders.of(Map.of("content-type", List.of("text/plain")), (k, v) -> true);
        when(response.headers()).thenReturn(headers);

        when(httpClient.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<InputStream>>any()))
                .thenReturn(response);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> pdfFetchService.fetchAndStorePdf(request));
        assertEquals("Unexpected status 500 while fetching PDF from https://example.com/error.pdf", thrown.getMessage());
        verify(storage, never()).writer(any(BlobInfo.class));
    }

    @Test
    void fetchAndStorePdf_existingObjectSkipsFetch() throws Exception {
        DocumentDiscoveryRequest request = new DocumentDiscoveryRequest();
        request.setMeetingId("meeting-123");
        request.setMeetingUrl("https://example.com/meeting.pdf");

        Blob existingBlob = mock(Blob.class);
        when(existingBlob.getContentType()).thenReturn("application/pdf");
        when(existingBlob.getSize()).thenReturn(2048L);
        when(storage.get(BlobId.of("test-bucket", "meetings/meeting-123.pdf"))).thenReturn(existingBlob);

        PdfFetchOutcome result = pdfFetchService.fetchAndStorePdf(request);

        assertEquals(PdfFetchStatus.REUSED, result.status());
        assertEquals(existingBlob, result.blob());
        verify(httpClient, never()).send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<InputStream>>any());
        verify(storage, never()).writer(any(BlobInfo.class));
    }
}
