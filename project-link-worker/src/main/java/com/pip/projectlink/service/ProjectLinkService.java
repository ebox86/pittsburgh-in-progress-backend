package com.pip.projectlink.service;

import com.pip.ingest.model.ProjectCandidateEvent;
import com.pip.projectlink.analysis.DocumentAnalysisStrategy;
import com.pip.projectlink.config.ProjectLinkConfig;
import com.pip.projectlink.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class ProjectLinkService {

    private static final Logger log = LoggerFactory.getLogger(ProjectLinkService.class);

    private final DocumentAnalysisStrategy documentAnalysisStrategy;
    private final ProjectLinkConfig projectLinkConfig;
    private final Optional<ProjectRepository> projectRepository;
    private final ConcurrentMap<String, LinkOutcome> memoryOutcomes = new ConcurrentHashMap<>();

    public ProjectLinkService(DocumentAnalysisStrategy documentAnalysisStrategy,
                              ProjectLinkConfig projectLinkConfig,
                              Optional<ProjectRepository> projectRepository) {
        this.documentAnalysisStrategy = documentAnalysisStrategy;
        this.projectLinkConfig = projectLinkConfig;
        this.projectRepository = projectRepository;
    }

    public ProjectLinkResult linkProject(ProjectCandidateEvent event) {
        ProjectCandidateEvent enriched = documentAnalysisStrategy.enrichFromDocument(event);
        String projectKey = determineProjectKey(enriched);
        LinkOutcome memoryOutcome = memoryOutcomes.compute(projectKey, (key, existing) -> existing == null ? LinkOutcome.CREATED : LinkOutcome.UPDATED);
        ProjectLinkResult result = new ProjectLinkResult(projectKey, memoryOutcome);

        Optional<ProjectLinkResult> firestoreResult = Optional.empty();
        if (isFirestoreEnabled()) {
            try {
                firestoreResult = Optional.of(projectRepository.get().upsertFromCandidate(projectKey, enriched));
                result = firestoreResult.get();
            } catch (Exception e) {
                log.error("Failed to persist project {} to Firestore (key={})", projectKey, e);
            }
        }

        log.info("Linked project {} store={} outcome={} key={} projectId={} environment={}",
                enriched.meetingId(),
                determineStoreName(firestoreResult.isPresent()),
                result.outcome(),
                projectKey,
                projectLinkConfig.getProjectId(),
                projectLinkConfig.getEnvironment());

        return result;
    }

    private boolean isFirestoreEnabled() {
        return projectRepository.isPresent() && "firestore".equalsIgnoreCase(projectLinkConfig.getProjectsStore());
    }

    private String determineStoreName(boolean firestoreUsed) {
        if (firestoreUsed) {
            return "firestore";
        }
        String store = projectLinkConfig.getProjectsStore();
        return store == null || store.isBlank() ? "memory" : store;
    }

    private String determineProjectKey(ProjectCandidateEvent event) {
        if (event == null) {
            return "<unknown>";
        }
        if (event.meetingId() != null && !event.meetingId().isBlank()) {
            return event.meetingId();
        }
        String bucket = event.bucket();
        String objectName = event.objectName();
        if (bucket == null || bucket.isBlank()) {
            bucket = "<unknown-bucket>";
        }
        if (objectName == null || objectName.isBlank()) {
            objectName = "<unknown-object>";
        }
        return bucket + "/" + objectName;
    }
}
