package com.pip.projectlink.analysis;

import com.pip.ingest.model.ProjectCandidateEvent;

public interface DocumentAnalysisStrategy {

    ProjectCandidateEvent enrichFromDocument(ProjectCandidateEvent baseEvent);
}
