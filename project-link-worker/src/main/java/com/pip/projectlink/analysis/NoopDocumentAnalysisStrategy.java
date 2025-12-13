package com.pip.projectlink.analysis;

import com.pip.ingest.model.ProjectCandidateEvent;
import org.springframework.stereotype.Component;

@Component
public class NoopDocumentAnalysisStrategy implements DocumentAnalysisStrategy {

    @Override
    public ProjectCandidateEvent enrichFromDocument(ProjectCandidateEvent baseEvent) {
        // TODO: Replace with Document AI / heuristic enrichment when the feature is ready.
        return baseEvent;
    }
}
