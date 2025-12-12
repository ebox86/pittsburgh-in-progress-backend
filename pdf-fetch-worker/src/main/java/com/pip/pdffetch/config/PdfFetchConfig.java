package com.pip.pdffetch.config;

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
public class PdfFetchConfig {

    private static final Logger log = LoggerFactory.getLogger(PdfFetchConfig.class);

    private final String projectId;
    private final String bucket;
    private final String pdfStoredTopic;

    public PdfFetchConfig(
            @Value("${pip.project-id}") String projectId,
            @Value("${pip.pdf-bucket}") String bucket,
            @Value("${pip.pdf-stored-topic}") String pdfStoredTopic) {
        Assert.hasText(projectId, "pip.project-id must be set");
        Assert.hasText(bucket, "pip.pdf-bucket must be set");
        Assert.hasText(pdfStoredTopic, "pip.pdf-stored-topic must be set");
        this.projectId = projectId;
        this.bucket = bucket;
        this.pdfStoredTopic = pdfStoredTopic;
    }

    @Bean
    public Storage storage() {
        return StorageOptions.getDefaultInstance().getService();
    }

    @Bean(destroyMethod = "shutdown")
    public Publisher pdfStoredPublisher() {
        TopicName topic = TopicName.of(projectId, pdfStoredTopic);
        try {
            return Publisher.newBuilder(topic).build();
        } catch (IOException e) {
            log.error("failed to create Publisher for {}", topic, e);
            throw new IllegalStateException("Unable to build Pub/Sub publisher", e);
        }
    }

    public String getBucket() {
        return bucket;
    }

    public String getPdfStoredTopic() {
        return pdfStoredTopic;
    }
}
