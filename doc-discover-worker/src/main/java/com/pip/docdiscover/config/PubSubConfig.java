package com.pip.docdiscover.config;

import com.google.cloud.pubsub.v1.Publisher;
import com.google.pubsub.v1.TopicName;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class PubSubConfig {

    @Value("${pip.project-id}")
    private String projectId;

    @Value("${pip.document-topic}")
    private String documentTopic;

    @Bean(destroyMethod = "shutdown")
    public Publisher documentPublisher() throws Exception {
        String project = projectId != null ? projectId.trim() : "";
        String topic = documentTopic != null ? documentTopic.trim() : "";

        if (!StringUtils.hasText(project)) {
            throw new IllegalArgumentException("pip.project-id must be provided");
        }
        if (!StringUtils.hasText(topic)) {
            throw new IllegalArgumentException("pip.document-topic must be provided");
        }

        TopicName topicName = TopicName.of(project, topic);
        return Publisher.newBuilder(topicName).build();
    }
}
