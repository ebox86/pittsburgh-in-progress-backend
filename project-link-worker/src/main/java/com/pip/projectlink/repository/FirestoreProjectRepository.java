package com.pip.projectlink.repository;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.SetOptions;
import com.pip.ingest.model.ProjectCandidateEvent;
import com.pip.projectlink.config.ProjectStoreProperties;
import com.pip.projectlink.model.ProjectDocument;
import com.pip.projectlink.model.SourceInfo;
import com.pip.projectlink.service.LinkOutcome;
import com.pip.projectlink.service.ProjectLinkResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.concurrent.ExecutionException;

@Component
@ConditionalOnProperty(prefix = "pip.projects", name = "store", havingValue = "firestore")
public class FirestoreProjectRepository implements ProjectRepository {

    private final Firestore firestore;
    private final String collectionName;

    public FirestoreProjectRepository(Firestore firestore, ProjectStoreProperties properties) {
        this.firestore = firestore;
        this.collectionName = properties.getCollection();
    }

    @Override
    public ProjectLinkResult upsertFromCandidate(String projectKey, ProjectCandidateEvent candidate) {
        try {
            DocumentReference reference = firestore.collection(collectionName).document(projectKey);
            DocumentSnapshot snapshot = reference.get().get();
            boolean exists = snapshot.exists();
            ProjectDocument existing = exists ? snapshot.toObject(ProjectDocument.class) : null;
            Timestamp now = Timestamp.now();
            ProjectDocument document = existing != null ? existing : new ProjectDocument();
            document.setProjectId(projectKey);
            document.setMeetingId(candidate.meetingId());
            document.setMeetingType(candidate.meetingType());
            document.setMeetingDate(parseMeetingDate(candidate.meetingDate()));
            document.setSource(SourceInfo.fromCandidate(candidate));
            document.setLastSeenAt(now);
            document.setUpdatedAt(now);

            if (!exists) {
                document.setStatus("candidate");
                document.setFirstSeenAt(now);
                document.setCreatedAt(now);
            } else {
                if (document.getFirstSeenAt() == null) {
                    document.setFirstSeenAt(now);
                }
                if (document.getCreatedAt() == null) {
                    document.setCreatedAt(now);
                }
            }

            reference.set(document, SetOptions.merge()).get();
            return new ProjectLinkResult(projectKey, exists ? LinkOutcome.UPDATED : LinkOutcome.CREATED);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while writing project document", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to persist project document", e);
        }
    }

    private Timestamp parseMeetingDate(String rawDate) {
        if (rawDate == null || rawDate.isBlank()) {
            return null;
        }
        try {
            return Timestamp.parseTimestamp(rawDate);
        } catch (RuntimeException e) {
            try {
                LocalDate date = LocalDate.parse(rawDate);
                return Timestamp.ofTimeSecondsAndNanos(date.atStartOfDay(ZoneOffset.UTC).toInstant().getEpochSecond(), 0);
            } catch (DateTimeParseException ignored) {
                return Timestamp.now();
            }
        }
    }
}
