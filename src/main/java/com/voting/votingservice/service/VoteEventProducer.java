package com.voting.votingservice.service;

import com.voting.votingservice.dto.VoteCastEvent;
import com.voting.votingservice.dto.VoteVerificationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes vote lifecycle events to Kafka topics.
 * <p>
 * Uses the election ID as the partition key for {@code vote.cast} events,
 * ensuring all votes for the same election land on the same partition
 * for ordered processing in the Result Service.
 * <p>
 * Events are sent after the vote is committed, so a broker failure must not fail the request: it is
 * logged with the vote id (the vote itself is safe in the database; live results miss it until a recount).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VoteEventProducer {

    static final String VOTE_CAST_TOPIC = "vote.cast";
    static final String VOTE_VERIFIED_TOPIC = "vote.verified";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishVoteCast(VoteCastEvent event) {
        send(VOTE_CAST_TOPIC, event.getElectionId().toString(), event, event.getVoteId().toString());
    }

    public void publishVoteVerified(VoteVerificationResponse verification) {
        String voteId = verification.getVoteId().toString();
        send(VOTE_VERIFIED_TOPIC, voteId, verification, voteId);
    }

    private void send(String topic, String key, Object payload, String voteId) {
        try {
            kafkaTemplate.send(topic, key, payload).whenComplete((result, error) -> {
                if (error != null) {
                    log.error("Failed to publish {} for vote {}: {}", topic, voteId, error.getMessage());
                }
            });
            log.info("Published {} for vote {}", topic, voteId);
        } catch (KafkaException e) {
            log.error("Failed to publish {} for vote {}: {}", topic, voteId, e.getMessage());
        }
    }
}
