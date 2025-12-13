package com.pip.projectlink.repository;

import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import com.google.cloud.firestore.SetOptions;
import com.pip.ingest.model.ProjectCandidateEvent;
import com.pip.projectlink.config.ProjectStoreProperties;
import com.pip.projectlink.model.ProjectDocument;
import com.pip.projectlink.service.LinkOutcome;
import com.pip.projectlink.service.ProjectLinkResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FirestoreProjectRepositoryTest {

    private Firestore firestore;
    private CollectionReference collection;
    private DocumentReference documentReference;
    private ApiFuture<DocumentSnapshot> getFuture;
    private ApiFuture<WriteResult> writeFuture;
    private DocumentSnapshot snapshot;
    private FirestoreProjectRepository repository;

    @BeforeEach
    void setUp() {
        firestore = mock(Firestore.class);
        collection = mock(CollectionReference.class);
        documentReference = mock(DocumentReference.class);
        getFuture = mock(ApiFuture.class);
        writeFuture = mock(ApiFuture.class);
        snapshot = mock(DocumentSnapshot.class);

        ProjectStoreProperties properties = new ProjectStoreProperties();
        properties.setCollection("projects");
        repository = new FirestoreProjectRepository(firestore, properties);
    }

    @Test
    void upsertFromCandidate_createsDocument() throws Exception {
        prepareDocumentLoad("meeting-1", false);
        when(documentReference.set(any(ProjectDocument.class), eq(SetOptions.merge()))).thenReturn(writeFuture);
        when(writeFuture.get()).thenReturn(mock(WriteResult.class));

        ProjectLinkResult result = repository.upsertFromCandidate("meeting-1", candidate("meeting-1"));

        assertEquals(LinkOutcome.CREATED, result.outcome());
    }

    @Test
    void upsertFromCandidate_updatesDocument() throws Exception {
        ProjectDocument existing = new ProjectDocument();
        Timestamp firstSeen = Timestamp.now();
        existing.setFirstSeenAt(firstSeen);
        existing.setCreatedAt(firstSeen);
        existing.setProjectId("meeting-1");
        when(snapshot.toObject(ProjectDocument.class)).thenReturn(existing);
        prepareDocumentLoad("meeting-1", true);
        when(documentReference.set(any(ProjectDocument.class), eq(SetOptions.merge()))).thenReturn(writeFuture);
        when(writeFuture.get()).thenReturn(mock(WriteResult.class));

        ProjectLinkResult result = repository.upsertFromCandidate("meeting-1", candidate("meeting-1"));

        assertEquals(LinkOutcome.UPDATED, result.outcome());
        ArgumentCaptor<ProjectDocument> captor = ArgumentCaptor.forClass(ProjectDocument.class);
        verify(documentReference, times(1)).set(captor.capture(), eq(SetOptions.merge()));
        ProjectDocument stored = captor.getValue();
        assertEquals(firstSeen, stored.getFirstSeenAt());
        assertEquals(firstSeen, stored.getCreatedAt());
    }

    private void prepareDocumentLoad(String key, boolean exists) throws InterruptedException, ExecutionException {
        when(firestore.collection("projects")).thenReturn(collection);
        when(collection.document(key)).thenReturn(documentReference);
        when(documentReference.get()).thenReturn(getFuture);
        when(getFuture.get()).thenReturn(snapshot);
        when(snapshot.exists()).thenReturn(exists);
    }

    private ProjectCandidateEvent candidate(String meetingId) {
        return new ProjectCandidateEvent(
                "project-candidate",
                meetingId,
                "planning",
                "2025-11-01",
                "https://example.com/original.pdf",
                "test-bucket",
                "document.pdf",
                5,
                1024L,
                "initial text");
    }
}
