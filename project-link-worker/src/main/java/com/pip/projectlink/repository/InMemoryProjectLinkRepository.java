package com.pip.projectlink.repository;

import com.pip.ingest.model.ProjectCandidateEvent;
import com.pip.projectlink.model.ProjectRecord;
import com.pip.projectlink.service.LinkOutcome;
import com.pip.projectlink.service.ProjectLinkResult;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class InMemoryProjectLinkRepository implements ProjectLinkRepository {

    private final ConcurrentMap<String, ProjectRecord> store = new ConcurrentHashMap<>();

    @Override
    public ProjectLinkResult upsert(ProjectCandidateEvent event) {
        Objects.requireNonNull(event, "ProjectCandidateEvent cannot be null");
        String key = determineProjectKey(event);
        ProjectRecord record = ProjectRecord.from(event);
        ProjectRecord previous = store.put(key, record);
        LinkOutcome outcome = previous == null ? LinkOutcome.CREATED : LinkOutcome.UPDATED;
        return new ProjectLinkResult(key, outcome);
    }

    private String determineProjectKey(ProjectCandidateEvent event) {
        if (StringUtils.hasText(event.meetingId())) {
            return event.meetingId();
        }
        String bucket = StringUtils.hasText(event.bucket()) ? event.bucket() : "<unknown-bucket>";
        String objectName = StringUtils.hasText(event.objectName()) ? event.objectName() : "<unknown-object>";
        return bucket + "/" + objectName;
    }
}
