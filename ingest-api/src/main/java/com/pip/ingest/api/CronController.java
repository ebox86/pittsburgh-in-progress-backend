package com.pip.ingest.api;

import com.pip.ingest.service.MeetingsPublisherService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class CronController {

    private final MeetingsPublisherService meetingsPublisherService;

    public CronController(MeetingsPublisherService meetingsPublisherService) {
        this.meetingsPublisherService = meetingsPublisherService;
    }

    @PostMapping("/cron/meetings")
    public ResponseEntity<Map<String, String>> publishMeeting() {
        meetingsPublisherService.publishFakeMeeting();
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
