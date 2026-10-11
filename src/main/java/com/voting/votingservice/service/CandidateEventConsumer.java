package com.voting.votingservice.service;

import com.voting.votingservice.dto.CandidateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Keeps {@link CandidateStatusCache} in step with the Candidate Service, so a disqualified or deleted
 * candidate is refused even while the Candidate Service is down.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CandidateEventConsumer {

    private static final String DELETED = "DELETED";

    private final CandidateStatusCache statusCache;

    @KafkaListener(topics = {"candidate.created", "candidate.status-changed"}, groupId = "voting-service-group")
    public void onCandidateChanged(CandidateEvent event) {
        if (!event.isComplete()) {
            log.warn("Ignoring incomplete candidate event: {}", event);
            return;
        }
        statusCache.put(event.id(), event.electionId(), event.status());
    }

    @KafkaListener(topics = "candidate.deleted", groupId = "voting-service-group")
    public void onCandidateDeleted(CandidateEvent event) {
        if (event.id() == null || event.electionId() == null) {
            log.warn("Ignoring incomplete candidate.deleted event: {}", event);
            return;
        }
        statusCache.put(event.id(), event.electionId(), DELETED);
    }
}
