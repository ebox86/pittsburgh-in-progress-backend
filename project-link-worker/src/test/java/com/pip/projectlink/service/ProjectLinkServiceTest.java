package com.pip.projectlink.service;

import com.pip.ingest.model.ProjectCandidateEvent;
import com.pip.projectlink.analysis.NoopDocumentAnalysisStrategy;
import com.pip.projectlink.config.ProjectLinkConfig;
import com.pip.projectlink.config.ProjectStoreProperties;
import com.pip.projectlink.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectLinkServiceTest {

    private ProjectCandidateEvent event;

    @BeforeEach
    void setUp() {
        event = new ProjectCandidateEvent(
                "project-candidate",
                "meeting-1",
                "planning",
                "2025-11-01",
                "https://example.com/original.pdf",
                "test-bucket",
                "document.pdf",
                5,
                1024L,
                "initial text");
    }

    @Test
    void linkProject_memoryOnly() {
        ProjectLinkService service = createService("memory", Optional.empty());

        ProjectLinkResult first = service.linkProject(event);
        assertEquals(LinkOutcome.CREATED, first.outcome());

        ProjectCandidateEvent updated = new ProjectCandidateEvent(
                "project-candidate",
                "meeting-1",
                "planning",
                "2025-11-02",
                "https://example.com/original.pdf",
                "test-bucket",
                "document.pdf",
                5,
                2048L,
                "updated text");

        ProjectLinkResult second = service.linkProject(updated);
        assertEquals(LinkOutcome.UPDATED, second.outcome());
        assertEquals(first.projectKey(), second.projectKey());
    }

    @Test
    void linkProject_usesFirestoreRepositoryWhenConfigured() {
        ProjectRepository repository = mock(ProjectRepository.class);
        when(repository.upsertFromCandidate(eq("meeting-1"), any()))
                .thenReturn(new ProjectLinkResult("meeting-1", LinkOutcome.CREATED))
                .thenReturn(new ProjectLinkResult("meeting-1", LinkOutcome.UPDATED));

        ProjectLinkService service = createService("firestore", Optional.of(repository));

        ProjectLinkResult first = service.linkProject(event);
        assertEquals(LinkOutcome.CREATED, first.outcome());

        ProjectLinkResult second = service.linkProject(event);
        assertEquals(LinkOutcome.UPDATED, second.outcome());

        ArgumentCaptor<ProjectCandidateEvent> captor = ArgumentCaptor.forClass(ProjectCandidateEvent.class);
        verify(repository, times(2)).upsertFromCandidate(eq("meeting-1"), captor.capture());
        assertEquals("meeting-1", captor.getValue().meetingId());
    }

    private ProjectLinkService createService(String store, Optional<ProjectRepository> repository) {
        ProjectStoreProperties storeProperties = new ProjectStoreProperties();
        storeProperties.setStore(store);
        storeProperties.setCollection("projects");
        ProjectLinkConfig config = new ProjectLinkConfig("test-project", "dev", storeProperties);
        return new ProjectLinkService(new NoopDocumentAnalysisStrategy(), config, repository);
    }
}
