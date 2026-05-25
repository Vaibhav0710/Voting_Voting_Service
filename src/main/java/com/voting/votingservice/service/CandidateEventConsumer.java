package com.voting.votingservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
@Slf4j
public class CandidateEventConsumer {

    @KafkaListener(topics = "candidate.status-changed", groupId = "voting-service-group")
    public void consumeCandidateStatusChanged(Map<String, Object> eventPayload) {
        log.info("Consumed candidate.status-changed event: {}", eventPayload);
        // Here we could update Redis or local cache to immediately disqualify votes for this candidate
        // For example:
        // redisTemplate.opsForValue().set("candidate:" + eventPayload.get("id") + ":status", eventPayload.get("status"));
    }
}
