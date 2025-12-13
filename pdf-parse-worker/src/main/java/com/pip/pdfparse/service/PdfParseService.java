package com.pip.pdfparse.service;

import com.google.cloud.ReadChannel;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.pip.ingest.model.PdfStoredEvent;
import com.pip.ingest.model.ProjectCandidateEvent;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;

@Service
public class PdfParseService {

    private static final Logger log = LoggerFactory.getLogger(PdfParseService.class);
    private static final int MAX_TEXT_CHARS = 100_000;

    private final Storage storage;
    private final ProjectCandidatePublisherService publisher;

    public PdfParseService(Storage storage, ProjectCandidatePublisherService publisher) {
        this.storage = storage;
        this.publisher = publisher;
    }

    public PdfParseResultStatus parseAndPublish(PdfStoredEvent event) {
        BlobId blobId = BlobId.of(event.getBucket(), event.getObjectName());
        Blob blob = storage.get(blobId);

        try (ReadChannel readChannel = storage.reader(event.getBucket(), event.getObjectName());
             InputStream in = Channels.newInputStream(readChannel);
             PDDocument document = PDDocument.load(in)) {

            PDFTextStripper stripper = new PDFTextStripper();
            String text = truncate(stripper.getText(document), MAX_TEXT_CHARS);
            int pageCount = document.getNumberOfPages();
            long sizeBytes = blob != null ? blob.getSize() : event.getSizeBytes();

            ProjectCandidateEvent parsedEvent = new ProjectCandidateEvent(
                    "pdf-parsed",
                    event.getMeetingId(),
                    event.getMeetingType(),
                    event.getMeetingDate(),
                    event.getSourceUrl(),
                    event.getBucket(),
                    event.getObjectName(),
                    pageCount,
                    sizeBytes,
                    text);

            publisher.publishProjectCandidate(parsedEvent);
            log.info("Published project-candidate event for {} with {} pages", event.getMeetingId(), pageCount);
            return PdfParseResultStatus.PARSED_AND_PUBLISHED;
        } catch (IOException e) {
            log.error("Failed to parse PDF for {} at gs://{}/{}", event.getMeetingId(), event.getBucket(), event.getObjectName(), e);
            throw new IllegalStateException("Failed to parse PDF", e);
        }
    }

    private static String truncate(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars);
    }
}
