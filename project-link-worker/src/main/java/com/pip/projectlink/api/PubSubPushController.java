package com.pip.projectlink.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pip.ingest.model.ProjectCandidateEvent;
import com.pip.ingest.pubsub.PubSubMessage;
import com.pip.ingest.pubsub.PubSubPushEnvelope;
import com.pip.projectlink.config.ProjectLinkConfig;
import com.pip.projectlink.service.ProjectLinkResult;
import com.pip.projectlink.service.ProjectLinkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.Map;

@RestController
public class PubSubPushController {

    private static final Logger log = LoggerFactory.getLogger(PubSubPushController.class);

    private final ObjectMapper objectMapper;
    private final ProjectLinkService projectLinkService;
    private final ProjectLinkConfig projectLinkConfig;

    public PubSubPushController(ObjectMapper objectMapper,
                                ProjectLinkService projectLinkService,
                                ProjectLinkConfig projectLinkConfig) {
        this.objectMapper = objectMapper;
        this.projectLinkService = projectLinkService;
        this.projectLinkConfig = projectLinkConfig;
    }

    @PostMapping("/")
    public ResponseEntity<Void> handlePubSubPush(@RequestBody(required = false) PubSubPushEnvelope envelope) {
        try {
            if (envelope == null || envelope.getMessage() == null || envelope.getMessage().getData() == null) {
                log.warn("Received Pub/Sub push without data");
                return ResponseEntity.badRequest().build();
            }

            PubSubMessage message = envelope.getMessage();
            String messageContext = describePubSubMessage(message);
            byte[] decoded = Base64.getDecoder().decode(message.getData());
            ProjectCandidateEvent event = objectMapper.readValue(decoded, ProjectCandidateEvent.class);
            log.info("Received project-candidate event for {} from {}/{} (subscription={}, {})",
                    event.meetingId(),
                    event.bucket(),
                    event.objectName(),
                    envelope.getSubscription(),
                    messageContext);

            ProjectLinkResult result = projectLinkService.linkProject(event);
            log.info("Linked project {} outcome={} key={} projectId={} environment={} store={}",
                    event.meetingId(),
                    result.outcome(),
                    result.projectKey(),
                    projectLinkConfig.getProjectId(),
                    projectLinkConfig.getEnvironment(),
                    projectLinkConfig.getProjectsStore());

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to process project-candidate push", e);
            return ResponseEntity.status(500).build();
        }
    }

    private static String describePubSubMessage(PubSubMessage message) {
        if (message == null) {
            return "messageId=<unknown> attributes={}";
        }
        String messageId = message.getMessageId() == null ? "<unknown>" : message.getMessageId();
        Map<String, String> attrs = message.getAttributes();
        return "messageId=" + messageId + " attributes=" + (attrs == null ? "{}" : attrs);
    }
}
