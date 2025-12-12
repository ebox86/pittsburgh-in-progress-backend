package com.pip.pdfparse.model;

public record PdfParsedEvent(
        String type,
        String meetingId,
        String meetingType,
        String meetingDate,
        String sourceUrl,
        String bucket,
        String objectName,
        int pageCount,
        long sizeBytes,
        String text
) {
}
