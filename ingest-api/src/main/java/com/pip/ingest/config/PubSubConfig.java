package com.pip.ingest.config;

import com.google.cloud.pubsub.v1.Publisher;
import com.google.pubsub.v1.TopicName;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.Objects;

@Configuration
public class PubSubConfig {

    @Value("${pip.project-id:#{systemEnvironment['PIP_PROJECT_ID'] ?: systemEnvironment['GOOGLE_CLOUD_PROJECT']}}")
    private String projectId;

    @Value("${pip.meeting-topic:pgh-meeting-discovered}")
    private String meetingTopic;

    @Bean(destroyMethod = "shutdown")
    public Publisher meetingPublisher() throws IOException {
        String project = projectId != null ? projectId.trim() : null;
        if (project == null || project.isBlank()) {
            throw new IllegalStateException("Google Cloud project ID must be configured via pip.project-id or PIP_PROJECT_ID/GOOGLE_CLOUD_PROJECT");
        }

        String topic = meetingTopic != null ? meetingTopic.trim() : "";
        if (topic.isBlank()) {
            throw new IllegalStateException("Meeting topic must be configured via pip.meeting-topic");
        }

        TopicName topicName = TopicName.of(project, topic);
        return Publisher.newBuilder(topicName).build();
    }
}
