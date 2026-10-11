package com.voting.votingservice.service;

import com.voting.votingservice.dto.VoteCastEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs the side effects of a cast vote only once its database transaction has committed,
 * so Redis and Kafka never announce a vote that was rolled back.
 */
@Component
@RequiredArgsConstructor
public class VoteCastSideEffects {

    private final VoteRedisStore redisStore;
    private final VoteEventProducer voteEventProducer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVoteRecorded(VoteRecordedEvent event) {
        VoteCastEvent vote = event.voteCast();
        redisStore.markVoted(vote.getUserId(), vote.getElectionId());
        redisStore.rememberIdempotentVote(vote.getUserId(), event.idempotencyKey(), vote.getVoteId());
        voteEventProducer.publishVoteCast(vote);
    }
}
