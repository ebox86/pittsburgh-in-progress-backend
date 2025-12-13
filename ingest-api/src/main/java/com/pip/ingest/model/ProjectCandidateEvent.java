package com.pip.ingest.model;

public record ProjectCandidateEvent(
        String type,
        String meetingId,
        String meetingType,
        String meetingDate,
        String originalUrl,
        String bucket,
        String objectName,
        int pageCount,
        long sizeBytes,
        String text
) {
}
