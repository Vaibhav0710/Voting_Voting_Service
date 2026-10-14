package com.voting.votingservice.service;

import com.voting.votingservice.config.VotingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The fallback cache that lets voting continue while the Candidate Service is down. */
@ExtendWith(MockitoExtension.class)
class CandidateStatusCacheTest {

    private static final UUID CANDIDATE = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID ELECTION = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER_ELECTION = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final String KEY = "vot:test:candidate:" + CANDIDATE;
    private static final RedisConnectionFailureException REDIS_DOWN = new RedisConnectionFailureException("down");

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private HashOperations<String, Object, Object> hashOperations;

    private CandidateStatusCache cache;

    @BeforeEach
    void setUp() {
        VotingProperties properties = new VotingProperties(
                new VotingProperties.Redis("vot:test:", 60, 60),
                new VotingProperties.Chain(true));
        cache = new CandidateStatusCache(redisTemplate, properties);
    }

    @Nested
    class Find {

        @BeforeEach
        void useHashOperations() {
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        }

        @Test
        void returnsCachedElectionAndStatus() {
            when(hashOperations.entries(KEY)).thenReturn(Map.of(
                    "electionId", ELECTION.toString(), "status", "ACTIVE"));

            assertThat(cache.find(CANDIDATE))
                    .contains(new CandidateStatusCache.Entry(ELECTION, "ACTIVE"));
        }

        @Test
        void returnsEmptyWhenNothingCached() {
            when(hashOperations.entries(KEY)).thenReturn(Map.of());

            assertThat(cache.find(CANDIDATE)).isEmpty();
        }

        @Test
        void returnsEmptyWhenAFieldIsMissing() {
            when(hashOperations.entries(KEY)).thenReturn(Map.of("electionId", ELECTION.toString()));

            assertThat(cache.find(CANDIDATE)).isEmpty();
        }

        @Test
        void returnsEmptyWhenRedisIsDown() {
            when(hashOperations.entries(KEY)).thenThrow(REDIS_DOWN);

            assertThat(cache.find(CANDIDATE)).isEmpty();
        }
    }

    @Nested
    class Put {

        @BeforeEach
        void useHashOperations() {
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        }

        @Test
        void storesElectionAndStatus() {
            cache.put(CANDIDATE, ELECTION, "ACTIVE");

            verify(hashOperations).putAll(KEY, Map.of("electionId", ELECTION.toString(), "status", "ACTIVE"));
        }

        @Test
        void swallowsRedisFailure() {
            doThrow(REDIS_DOWN).when(hashOperations).putAll(anyString(), anyMap());

            assertThatCode(() -> cache.put(CANDIDATE, ELECTION, "ACTIVE")).doesNotThrowAnyException();
        }
    }

    @Nested
    class EntryIsActiveIn {

        @Test
        void trueOnlyForActiveCandidateInSameElection() {
            assertThat(new CandidateStatusCache.Entry(ELECTION, "ACTIVE").isActiveIn(ELECTION)).isTrue();
        }

        @Test
        void falseForOtherElection() {
            assertThat(new CandidateStatusCache.Entry(ELECTION, "ACTIVE").isActiveIn(OTHER_ELECTION)).isFalse();
        }

        @Test
        void falseForDisqualifiedCandidate() {
            assertThat(new CandidateStatusCache.Entry(ELECTION, "DISQUALIFIED").isActiveIn(ELECTION)).isFalse();
        }
    }
}
