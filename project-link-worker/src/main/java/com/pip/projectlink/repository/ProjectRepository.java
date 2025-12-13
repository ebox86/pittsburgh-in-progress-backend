package com.pip.projectlink.repository;

import com.pip.ingest.model.ProjectCandidateEvent;
import com.pip.projectlink.service.ProjectLinkResult;

public interface ProjectRepository {

    ProjectLinkResult upsertFromCandidate(String projectKey, ProjectCandidateEvent candidate);
}
