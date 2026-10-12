package com.voting.votingservice.service;

import com.voting.votingservice.dto.VoteCastEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class VoteCastSideEffectsTest {

    @Mock private VoteRedisStore redisStore;
    @Mock private VoteEventProducer voteEventProducer;
    @Mock private VoteOutcomeRecorder outcomeRecorder;
    @InjectMocks private VoteCastSideEffects sideEffects;

    @Test
    void committedVote_setsRedisFlags_publishesEvent_andRecordsSuccess() {
        VoteCastEvent vote = VoteCastEvent.builder()
                .voteId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .candidateId(UUID.randomUUID())
                .electionId(UUID.randomUUID())
                .build();

        sideEffects.onVoteRecorded(new VoteRecordedEvent(vote, "key-1"));

        verify(redisStore).markVoted(vote.getUserId(), vote.getElectionId());
        verify(redisStore).rememberIdempotentVote(vote.getUserId(), "key-1", vote.getVoteId());
        verify(voteEventProducer).publishVoteCast(vote);
        verify(outcomeRecorder).cast(vote.getUserId(), vote.getVoteId());
    }
}
