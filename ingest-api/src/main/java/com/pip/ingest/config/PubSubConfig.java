package com.pip.ingest.config;

import com.google.cloud.pubsub.v1.Publisher;
import com.google.pubsub.v1.ProjectTopicName;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.Arrays;

@Configuration
public class PubSubConfig {

    @Bean
    public Publisher meetingsPublisher(@Value("${pip.meeting.topic:pgh-meeting-discovered}") String rawTopic) throws IOException {
        String topicName = rawTopic.trim();
        ProjectTopicName projectTopicName;

        if (topicName.startsWith("projects/")) {
            projectTopicName = ProjectTopicName.parse(topicName);
        } else {
            String projectId = resolveProjectId();
            projectTopicName = ProjectTopicName.of(projectId, topicName);
        }

        return Publisher.newBuilder(projectTopicName).build();
    }

    private String resolveProjectId() {
        return Arrays.stream(new String[] {
                        System.getenv("GOOGLE_CLOUD_PROJECT"),
                        System.getenv("GCP_PROJECT"),
                        System.getenv("GCLOUD_PROJECT")
                })
                .filter(p -> p != null && !p.isBlank())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Google Cloud project ID is not configured"));
    }
}
