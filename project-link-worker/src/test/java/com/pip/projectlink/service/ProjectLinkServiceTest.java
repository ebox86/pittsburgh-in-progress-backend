package com.pip.projectlink.service;

import com.pip.ingest.model.ProjectCandidateEvent;
import com.pip.projectlink.analysis.NoopDocumentAnalysisStrategy;
import com.pip.projectlink.repository.InMemoryProjectLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectLinkServiceTest {

    private ProjectLinkService service;

    @BeforeEach
    void setUp() {
        service = new ProjectLinkService(new InMemoryProjectLinkRepository(), new NoopDocumentAnalysisStrategy());
    }

    @Test
    void linkProject_returnsCreatedThenUpdated() {
        ProjectCandidateEvent event = new ProjectCandidateEvent(
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
}
