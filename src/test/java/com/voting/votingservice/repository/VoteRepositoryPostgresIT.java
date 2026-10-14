package com.voting.votingservice.repository;

import com.voting.votingservice.dto.ChainValidationResponse;
import com.voting.votingservice.dto.VoteRequest;
import com.voting.votingservice.exception.DuplicateVoteException;
import com.voting.votingservice.model.Vote;
import com.voting.votingservice.service.CandidateStatusCache;
import com.voting.votingservice.service.CandidateValidator;
import com.voting.votingservice.service.ChainHeadService;
import com.voting.votingservice.service.VoteEventProducer;
import com.voting.votingservice.service.VoteRedisStore;
import com.voting.votingservice.service.VoteService;
import com.voting.votingservice.support.ConcurrentTasks;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The database guarantees behind "one vote per voter" and the hash chain, checked on real PostgreSQL 16
 * (H2 only imitates it): the {@code uq_user_election} constraint, the {@code SELECT … FOR UPDATE} lock on the
 * chain head, and both under concurrent votes. Runs in {@code mvn verify}; skipped when Docker is not available.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class VoteRepositoryPostgresIT {

    private static final int THREADS = 20;
    private static final long LOCK_WAIT_MS = 500;
    private static final long DONE_WAIT_S = 10;
    private static final String SAMPLE_HASH = "a".repeat(64);

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.4-alpine");

    @DynamicPropertySource
    static void usePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired private VoteRepository voteRepository;
    @Autowired private ChainHeadService chainHeadService;
    @Autowired private VoteService voteService;
    @Autowired private TransactionTemplate transactionTemplate;

    @MockBean private VoteRedisStore redisStore;
    @MockBean private CandidateStatusCache candidateStatusCache;
    @MockBean private CandidateValidator candidateValidator;
    @MockBean private VoteEventProducer voteEventProducer;

    @Test
    void uniqueConstraint_rejectsSecondVoteBySameUserInSameElection() {
        UUID userId = UUID.randomUUID();
        UUID electionId = UUID.randomUUID();
        voteRepository.saveAndFlush(vote(userId, electionId, "first-" + userId));

        assertThatThrownBy(() -> voteRepository.saveAndFlush(vote(userId, electionId, "second-" + userId)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining("uq_user_election");
    }

    @Test
    void chainHeadLock_makesSecondTransactionWaitUntilFirstCommits() throws Exception {
        UUID electionId = UUID.randomUUID();
        transactionTemplate.executeWithoutResult(status -> chainHeadService.lockHead(electionId));
        CountDownLatch firstHoldsLock = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                chainHeadService.lockHead(electionId);
                firstHoldsLock.countDown();
                awaitOrFail(releaseFirst);
            }));
            assertThat(firstHoldsLock.await(DONE_WAIT_S, SECONDS)).isTrue();

            Future<?> second = pool.submit(() -> transactionTemplate.executeWithoutResult(
                    status -> chainHeadService.lockHead(electionId)));

            assertThatThrownBy(() -> second.get(LOCK_WAIT_MS, MILLISECONDS)).isInstanceOf(TimeoutException.class);
            releaseFirst.countDown();
            first.get(DONE_WAIT_S, SECONDS);
            second.get(DONE_WAIT_S, SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentVotesBySameUser_exactlyOneStored() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID electionId = UUID.randomUUID();
        AtomicInteger duplicates = new AtomicInteger();

        List<Boolean> outcomes = ConcurrentTasks.run(THREADS, i -> () -> {
            try {
                voteService.castVote(userId, request(electionId, "pg-same-user-" + userId + "-" + i));
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
            voteService.castVote(UUID.randomUUID(), request(electionId, "pg-user-" + electionId + "-" + i));
            return true;
        });

        ChainValidationResponse chain = voteService.validateChain(electionId);
        assertThat(chain.getTotalVotesChecked()).isEqualTo(THREADS);
        assertThat(chain.isChainValid()).isTrue();
    }

    private static Vote vote(UUID userId, UUID electionId, String idempotencyKey) {
        return Vote.builder()
                .userId(userId)
                .candidateId(UUID.randomUUID())
                .electionId(electionId)
                .voteHash(SAMPLE_HASH)
                .idempotencyKey(idempotencyKey)
                .timestamp(LocalDateTime.now())
                .build();
    }

    private static VoteRequest request(UUID electionId, String idempotencyKey) {
        return VoteRequest.builder()
                .candidateId(UUID.randomUUID())
                .electionId(electionId)
                .idempotencyKey(idempotencyKey)
                .build();
    }

    private static void awaitOrFail(CountDownLatch latch) {
        try {
            if (!latch.await(DONE_WAIT_S, SECONDS)) {
                throw new IllegalStateException("Test did not release the lock in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while holding the lock", e);
        }
    }
}
