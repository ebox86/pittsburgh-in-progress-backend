package com.pip.ingest.config;

import com.google.cloud.pubsub.v1.Publisher;
import com.google.pubsub.v1.TopicName;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class PubSubConfig {

    @Value("${pip.project-id:}")
    private String projectId;

    @Value("${pip.meeting-topic:pgh-meeting-discovered}")
    private String meetingTopic;

    @Bean(destroyMethod = "shutdown")
    public Publisher meetingPublisher() throws IOException {
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalStateException(
                "Project ID not configured. Set PIP_PROJECT_ID or GOOGLE_CLOUD_PROJECT / pip.project-id."
            );
        }

        String topic = meetingTopic != null ? meetingTopic.trim() : "";
        if (topic.isBlank()) {
            throw new IllegalStateException(
                "Meeting topic must be configured via pip.meeting-topic / PIP_MEETING_TOPIC"
            );
        }

        TopicName topicName = TopicName.of(projectId, topic);
        return Publisher.newBuilder(topicName).build();
    }
}
