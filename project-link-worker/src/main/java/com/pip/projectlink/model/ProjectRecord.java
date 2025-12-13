package com.pip.projectlink.model;

import com.pip.ingest.model.ProjectCandidateEvent;
import java.util.Objects;

public record ProjectRecord(
        String meetingId,
        String meetingType,
        String meetingDate,
        String originalUrl,
        String bucket,
        String objectName,
        int pageCount,
        long sizeBytes,
        String text) {

    public static ProjectRecord from(ProjectCandidateEvent event) {
        Objects.requireNonNull(event, "ProjectCandidateEvent is required");
        return new ProjectRecord(
                event.meetingId(),
                event.meetingType(),
                event.meetingDate(),
                event.originalUrl(),
                event.bucket(),
                event.objectName(),
                event.pageCount(),
                event.sizeBytes(),
                event.text());
    }
}
