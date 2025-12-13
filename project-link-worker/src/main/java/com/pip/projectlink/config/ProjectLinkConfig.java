package com.pip.projectlink.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ProjectLinkConfig {

    private final String projectId;
    private final String environment;
    private final String projectsStore;
    private final String projectsCollection;

    public ProjectLinkConfig(
            @Value("${pip.project-id:${PIP_PROJECT_ID:${GOOGLE_CLOUD_PROJECT:}}}") String projectId,
            @Value("${pip.environment:dev}") String environment,
            @Value("${pip.projects.store:memory}") String projectsStore,
            @Value("${pip.projects.collection:projects}") String projectsCollection) {
        this.projectId = projectId;
        this.environment = environment;
        this.projectsStore = projectsStore;
        this.projectsCollection = projectsCollection;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getProjectsStore() {
        return projectsStore;
    }

    public String getProjectsCollection() {
        return projectsCollection;
    }
}
