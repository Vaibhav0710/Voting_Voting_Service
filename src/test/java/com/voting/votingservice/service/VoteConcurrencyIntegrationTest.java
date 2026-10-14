package com.voting.votingservice.service;

import com.voting.votingservice.dto.ChainValidationResponse;
import com.voting.votingservice.dto.VoteRequest;
import com.voting.votingservice.exception.DuplicateVoteException;
import com.voting.votingservice.repository.VoteRepository;
import com.voting.votingservice.support.ConcurrentTasks;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Race-condition checks against a real database (H2 in PostgreSQL mode): the unique constraint and the
 * per-election chain lock must hold when many requests arrive at once and the Redis fast path misses.
 */
@SpringBootTest
@ActiveProfiles("test")
class VoteConcurrencyIntegrationTest {

    private static final int THREADS = 50;

    @Autowired private VoteService voteService;
    @Autowired private VoteRepository voteRepository;

    @MockBean private VoteRedisStore redisStore;
    @MockBean private CandidateStatusCache candidateStatusCache;
    @MockBean private CandidateValidator candidateValidator;
    @MockBean private VoteEventProducer voteEventProducer;

    @Test
    void fiftyConcurrentVotesBySameUser_exactlyOneStored() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID electionId = UUID.randomUUID();
        AtomicInteger duplicates = new AtomicInteger();

        List<Boolean> outcomes = ConcurrentTasks.run(THREADS, i -> () -> {
            try {
                voteService.castVote(userId, request(electionId, "same-user-" + i));
                return true;
            } catch (DuplicateVoteException e) {
                duplicates.incrementAndGet();
                return false;
            }
        });

        assertThat(outcomes.stream().filter(Boolean::booleanValue)).hasSize(1);
        assertThat(duplicates.get()).isEqualTo(THREADS - 1);
        assertThat(voteRepository.countByElectionId(electionId)).isEqualTo(1);
    }

    @Test
    void concurrentVotesByDifferentUsers_formOneUnbrokenChain() throws Exception {
        UUID electionId = UUID.randomUUID();

        ConcurrentTasks.run(THREADS, i -> () -> {
            voteService.castVote(UUID.randomUUID(), request(electionId, "user-" + i));
            return true;
        });

        ChainValidationResponse chain = voteService.validateChain(electionId);
        assertThat(chain.getTotalVotesChecked()).isEqualTo(THREADS);
        assertThat(chain.isChainValid()).isTrue();
    }

    private static VoteRequest request(UUID electionId, String idempotencyKey) {
        return VoteRequest.builder()
                .candidateId(UUID.randomUUID())
                .electionId(electionId)
                .idempotencyKey(idempotencyKey)
                .build();
    }
}
