package com.voting.votingservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class VoteEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishVoteCast(Object eventPayload) {
        log.info("Publishing vote.cast event: {}", eventPayload);
        kafkaTemplate.send("vote.cast", eventPayload);
    }

    public void publishVoteVerified(Object eventPayload) {
        log.info("Publishing vote.verified event: {}", eventPayload);
        kafkaTemplate.send("vote.verified", eventPayload);
    }
}
