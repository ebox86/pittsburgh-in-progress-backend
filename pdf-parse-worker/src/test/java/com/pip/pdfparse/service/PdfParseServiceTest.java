package com.pip.pdfparse.service;

import com.google.cloud.ReadChannel;
import com.google.cloud.RestorableState;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.pip.ingest.model.PdfStoredEvent;
import com.pip.ingest.model.ProjectCandidateEvent;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PdfParseServiceTest {

    @Mock
    private Storage storage;

    @Mock
    private ProjectCandidatePublisherService publisher;

    private PdfParseService pdfParseService;

    @BeforeEach
    void setUp() {
        pdfParseService = new PdfParseService(storage, publisher);
    }

    @Test
    void parseAndPublish_happyPath() throws Exception {
        PdfStoredEvent event = new PdfStoredEvent();
        event.setMeetingId("meeting-123");
        event.setSourceUrl("https://example.com/meeting.pdf");
        event.setBucket("test-bucket");
        event.setObjectName("test.pdf");
        event.setMeetingType("planning");
        event.setMeetingDate("2025-11-18");
        event.setSizeBytes(0);

        byte[] pdfBytes = createTestPdf("Hello");
        when(storage.reader("test-bucket", "test.pdf")).thenReturn(new ByteArrayReadChannel(pdfBytes));
        Blob blob = org.mockito.Mockito.mock(Blob.class);
        when(blob.getSize()).thenReturn((long) pdfBytes.length);
        when(storage.get(BlobId.of("test-bucket", "test.pdf"))).thenReturn(blob);

        PdfParseResultStatus status = pdfParseService.parseAndPublish(event);

        assertEquals(PdfParseResultStatus.PARSED_AND_PUBLISHED, status);
        ArgumentCaptor<ProjectCandidateEvent> captor = ArgumentCaptor.forClass(ProjectCandidateEvent.class);
        verify(publisher).publishProjectCandidate(captor.capture());
        ProjectCandidateEvent parsed = captor.getValue();
        assertEquals("meeting-123", parsed.meetingId());
        assertEquals("test-bucket", parsed.bucket());
        assertEquals("test.pdf", parsed.objectName());
        assertEquals("https://example.com/meeting.pdf", parsed.originalUrl());
    }

    private static byte[] createTestPdf(String text) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(doc, page)) {
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA, 12);
                contentStream.newLineAtOffset(100, 700);
                contentStream.showText(text);
                contentStream.endText();
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    private static final class ByteArrayReadChannel implements ReadChannel {
        private final ReadableByteChannel delegate;
        private final ByteArrayInputStream input;

        ByteArrayReadChannel(byte[] data) {
            this.input = new ByteArrayInputStream(data);
            this.delegate = java.nio.channels.Channels.newChannel(input);
        }

        @Override
        public int read(ByteBuffer dst) throws IOException {
            return delegate.read(dst);
        }

        @Override
        public void close() {
            try {
                delegate.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public boolean isOpen() {
            return delegate.isOpen();
        }

        @Override
        public void seek(long position) throws IOException {
            throw new UnsupportedOperationException("seek is not supported in tests");
        }

        @Override
        public void setChunkSize(int chunkSize) {
            // no-op
        }

        @Override
        public RestorableState<ReadChannel> capture() {
            return () -> this;
        }
    }
}
