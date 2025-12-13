package com.pip.projectlink.config;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

@Configuration
@EnableConfigurationProperties(ProjectStoreProperties.class)
public class FirestoreConfig {

    @Bean
    @ConditionalOnProperty(prefix = "pip.projects", name = "store", havingValue = "firestore")
    public Firestore firestore(@Value("${pip.project-id:${PIP_PROJECT_ID:${GOOGLE_CLOUD_PROJECT:}}}") String projectId) {
        Assert.hasText(projectId, "pip.project-id must be set to use Firestore");
        FirestoreOptions options = FirestoreOptions.getDefaultInstance().toBuilder()
                .setProjectId(projectId)
                .build();
        return options.getService();
    }
}
