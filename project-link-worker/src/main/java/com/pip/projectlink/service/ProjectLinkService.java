package com.pip.projectlink.service;

import com.pip.ingest.model.ProjectCandidateEvent;
import com.pip.projectlink.analysis.DocumentAnalysisStrategy;
import com.pip.projectlink.repository.ProjectLinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ProjectLinkService {

    private static final Logger log = LoggerFactory.getLogger(ProjectLinkService.class);

    private final ProjectLinkRepository repository;
    private final DocumentAnalysisStrategy documentAnalysisStrategy;

    public ProjectLinkService(ProjectLinkRepository repository,
                              DocumentAnalysisStrategy documentAnalysisStrategy) {
        this.repository = repository;
        this.documentAnalysisStrategy = documentAnalysisStrategy;
    }

    public ProjectLinkResult linkProject(ProjectCandidateEvent event) {
        ProjectCandidateEvent enriched = documentAnalysisStrategy.enrichFromDocument(event);
        ProjectLinkResult result = repository.upsert(enriched);
        log.info("Linked project {} outcome={} key={}", enriched.meetingId(), result.outcome(), result.projectKey());
        return result;
    }
}
