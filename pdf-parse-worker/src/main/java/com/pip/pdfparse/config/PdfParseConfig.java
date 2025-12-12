package com.pip.pdfparse.config;

import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.pubsub.v1.TopicName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

import java.io.IOException;

@Configuration
public class PdfParseConfig {

    private static final Logger log = LoggerFactory.getLogger(PdfParseConfig.class);

    private final String projectId;
    private final String projectCandidateTopic;

    public PdfParseConfig(
            @Value("${pip.project-id}") String projectId,
            @Value("${pip.project-candidate-topic}") String projectCandidateTopic) {
        Assert.hasText(projectId, "pip.project-id must be set");
        Assert.hasText(projectCandidateTopic, "pip.project-candidate-topic must be set");
        this.projectId = projectId;
        this.projectCandidateTopic = projectCandidateTopic;
    }

    @Bean
    public Storage storage() {
        return StorageOptions.getDefaultInstance().getService();
    }

    @Bean(destroyMethod = "shutdown")
    public Publisher projectCandidatePublisher() {
        TopicName topicName = TopicName.of(projectId, projectCandidateTopic);
        try {
            return Publisher.newBuilder(topicName).build();
        } catch (IOException e) {
            log.error("Failed to create project candidate publisher for {}", topicName, e);
            throw new IllegalStateException("Unable to build Pub/Sub publisher", e);
        }
    }

    public String getProjectCandidateTopic() {
        return projectCandidateTopic;
    }
}
