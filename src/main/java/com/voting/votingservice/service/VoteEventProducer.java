package com.voting.votingservice.service;

import com.voting.votingservice.dto.VoteCastEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes vote lifecycle events to Kafka topics.
 * <p>
 * Uses the election ID as the partition key for {@code vote.cast} events,
 * ensuring all votes for the same election land on the same partition
 * for ordered processing in the Result Service.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VoteEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishVoteCast(VoteCastEvent event) {
        log.info("Publishing vote.cast event for election={}, candidate={}", event.getElectionId(), event.getCandidateId());
        kafkaTemplate.send("vote.cast", event.getElectionId().toString(), event);
    }

    public void publishVoteVerified(Object eventPayload) {
        log.info("Publishing vote.verified event: {}", eventPayload);
        kafkaTemplate.send("vote.verified", eventPayload);
    }
}
