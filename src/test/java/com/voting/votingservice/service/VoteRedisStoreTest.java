package com.voting.votingservice.service;

import com.voting.votingservice.config.VotingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Redis is only a fast path: every Redis failure must read as a cache miss, never fail the vote. */
@ExtendWith(MockitoExtension.class)
class VoteRedisStoreTest {

    private static final long VOTED_TTL = 60;
    private static final long IDEMPOTENCY_TTL = 120;
    private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ELECTION = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final String VOTED_KEY = "vot:test:voted:" + USER + ":" + ELECTION;
    private static final String IDEMPOTENCY_KEY = "vot:test:idempotency:" + USER + ":key-1";
    private static final RedisConnectionFailureException REDIS_DOWN = new RedisConnectionFailureException("down");

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private VoteRedisStore store;

    @BeforeEach
    void setUp() {
        VotingProperties properties = new VotingProperties(
                new VotingProperties.Redis("vot:test:", VOTED_TTL, IDEMPOTENCY_TTL),
                new VotingProperties.Chain(true));
        store = new VoteRedisStore(redisTemplate, properties);
    }

    @Nested
    class HasVoted {

        @Test
        void returnsTrueWhenVotedFlagExists() {
            when(redisTemplate.hasKey(VOTED_KEY)).thenReturn(true);

            assertThat(store.hasVoted(USER, ELECTION)).isTrue();
        }

        @Test
        void returnsFalseWhenFlagMissing() {
            when(redisTemplate.hasKey(VOTED_KEY)).thenReturn(null);

            assertThat(store.hasVoted(USER, ELECTION)).isFalse();
        }

        @Test
        void returnsFalseWhenRedisIsDown_soTheDatabaseConstraintDecides() {
            when(redisTemplate.hasKey(VOTED_KEY)).thenThrow(REDIS_DOWN);

            assertThat(store.hasVoted(USER, ELECTION)).isFalse();
        }
    }

    @Nested
    class MarkVoted {

        @Test
        void setsFlagWithVotedTtl() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            store.markVoted(USER, ELECTION);

            verify(valueOperations).set(VOTED_KEY, "1", VOTED_TTL, TimeUnit.SECONDS);
        }

        @Test
        void swallowsRedisFailure() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            doThrow(REDIS_DOWN).when(valueOperations).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

            assertThatCode(() -> store.markVoted(USER, ELECTION)).doesNotThrowAnyException();
        }
    }

    @Nested
    class FindIdempotentVoteId {

        @Test
        void returnsStoredVoteId() {
            UUID voteId = UUID.randomUUID();
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(IDEMPOTENCY_KEY)).thenReturn(voteId.toString());

            assertThat(store.findIdempotentVoteId(USER, "key-1")).contains(voteId);
        }

        @Test
        void returnsEmptyWhenKeyUnknown() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(IDEMPOTENCY_KEY)).thenReturn(null);

            assertThat(store.findIdempotentVoteId(USER, "key-1")).isEmpty();
        }

        @Test
        void returnsEmptyWhenRedisIsDown_soTheDatabaseIsAsked() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(IDEMPOTENCY_KEY)).thenThrow(REDIS_DOWN);

            assertThat(store.findIdempotentVoteId(USER, "key-1")).isEmpty();
        }
    }

    @Nested
    class RememberIdempotentVote {

        @Test
        void storesVoteIdWithIdempotencyTtl() {
            UUID voteId = UUID.randomUUID();
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            store.rememberIdempotentVote(USER, "key-1", voteId);

            verify(valueOperations).set(IDEMPOTENCY_KEY, voteId.toString(), IDEMPOTENCY_TTL, TimeUnit.SECONDS);
        }

        @Test
        void swallowsRedisFailure() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            doThrow(REDIS_DOWN).when(valueOperations).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

            assertThatCode(() -> store.rememberIdempotentVote(USER, "key-1", UUID.randomUUID()))
                    .doesNotThrowAnyException();
        }
    }
}
