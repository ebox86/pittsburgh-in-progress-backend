package com.pip.projectlink.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ProjectLinkConfig {

    private final String projectId;
    private final String environment;
    private final ProjectStoreProperties projects;

    public ProjectLinkConfig(
            @Value("${pip.project-id:${PIP_PROJECT_ID:${GOOGLE_CLOUD_PROJECT:}}}") String projectId,
            @Value("${pip.environment:dev}") String environment,
            ProjectStoreProperties projects) {
        this.projectId = projectId;
        this.environment = environment;
        this.projects = projects;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getProjectsStore() {
        return projects.getStore();
    }

    public String getProjectsCollection() {
        return projects.getCollection();
    }
}
