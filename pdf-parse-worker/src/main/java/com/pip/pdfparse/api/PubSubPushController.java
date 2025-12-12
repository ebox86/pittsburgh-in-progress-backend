package com.pip.pdfparse.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pip.ingest.model.PdfStoredEvent;
import com.pip.ingest.pubsub.PubSubMessage;
import com.pip.ingest.pubsub.PubSubPushEnvelope;
import com.pip.pdfparse.service.PdfParseResultStatus;
import com.pip.pdfparse.service.PdfParseService;
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
    private final PdfParseService pdfParseService;

    public PubSubPushController(ObjectMapper objectMapper,
                                PdfParseService pdfParseService) {
        this.objectMapper = objectMapper;
        this.pdfParseService = pdfParseService;
    }

    @PostMapping("/")
    public ResponseEntity<Void> handlePubSubPush(@RequestBody PubSubPushEnvelope envelope) {
        try {
            PubSubMessage message = envelope == null ? null : envelope.getMessage();
            if (envelope == null || message == null || message.getData() == null) {
                log.warn("Received Pub/Sub push without data");
                return ResponseEntity.badRequest().build();
            }

            String context = describePubSubMessage(message);
            byte[] decoded = Base64.getDecoder().decode(message.getData());
            PdfStoredEvent event = objectMapper.readValue(decoded, PdfStoredEvent.class);
            log.info("Received pdf-stored event for {} from gs://{}/{} ({})",
                    event.getMeetingId(), event.getBucket(), event.getObjectName(), context);

            PdfParseResultStatus status = pdfParseService.parseAndPublish(event);
            log.info("Parse result for {}: {}", event.getMeetingId(), status);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to process pdf-stored event", e);
            return ResponseEntity.status(500).build();
        }
    }

    private static String describePubSubMessage(PubSubMessage message) {
        String messageId = message == null || message.getMessageId() == null ? "<unknown>" : message.getMessageId();
        Map<String, String> attrs = message == null ? null : message.getAttributes();
        return "messageId=" + messageId + " attributes=" + (attrs == null ? "{}" : attrs);
    }
}
