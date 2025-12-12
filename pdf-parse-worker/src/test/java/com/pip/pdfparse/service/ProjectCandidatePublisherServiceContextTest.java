package com.pip.pdfparse.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.pubsub.v1.TopicName;
import com.pip.pdfparse.config.PdfParseConfig;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.support.TestPropertySourceUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ProjectCandidatePublisherServiceContextTest {

    @Test
    void contextConstructsServiceWithConfiguredTopic() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        try {
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                    "pip.project-id=test-project",
                    "pip.project-candidate-topic=test-topic");
            context.register(TestConfig.class);
            context.refresh();

            ProjectCandidatePublisherService service = context.getBean(ProjectCandidatePublisherService.class);
            assertNotNull(service);

            PdfParseConfig config = context.getBean(PdfParseConfig.class);
            assertEquals("test-topic", config.getProjectCandidateTopic());

            TopicName topicName = context.getBean(TopicName.class);
            assertEquals("projects/test-project/topics/test-topic", topicName.toString());
        } finally {
            context.close();
        }
    }

    @Configuration
    static class TestConfig {

        @Bean
        PdfParseConfig pdfParseConfig(@Value("${pip.project-id}") String projectId,
                                      @Value("${pip.project-candidate-topic}") String projectCandidateTopic) {
            return new PdfParseConfig(projectId, projectCandidateTopic);
        }

        @Bean
        TopicName topicName(@Value("${pip.project-id}") String projectId,
                            @Value("${pip.project-candidate-topic}") String projectCandidateTopic) {
            return TopicName.of(projectId, projectCandidateTopic);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        Publisher projectCandidatePublisher() {
            return Mockito.mock(Publisher.class);
        }

        @Bean
        ProjectCandidatePublisherService projectCandidatePublisherService(Publisher publisher,
                                                                          ObjectMapper objectMapper,
                                                                          TopicName topicName) {
            return new ProjectCandidatePublisherService(publisher, objectMapper, topicName);
        }
    }
}
