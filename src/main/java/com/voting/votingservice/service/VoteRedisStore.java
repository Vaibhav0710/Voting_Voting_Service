package com.voting.votingservice.service;

import com.voting.votingservice.config.VotingProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Redis fast paths for vote casting: the "already voted" flag and the per-user idempotency record.
 * <p>
 * Redis is an optimisation, not the source of truth (the database unique constraints are), so every
 * Redis failure is logged and treated as a cache miss instead of failing the vote.
 */
@Slf4j
@Component
public class VoteRedisStore {

    private final StringRedisTemplate redisTemplate;
    private final VotingProperties.Redis settings;

    public VoteRedisStore(StringRedisTemplate redisTemplate, VotingProperties properties) {
        this.redisTemplate = redisTemplate;
        this.settings = properties.redis();
    }

    public boolean hasVoted(UUID userId, UUID electionId) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(votedKey(userId, electionId)));
        } catch (DataAccessException e) {
            log.warn("Redis voted-flag read failed, falling back to database constraint: {}", e.getMessage());
            return false;
        }
    }

    public void markVoted(UUID userId, UUID electionId) {
        try {
            redisTemplate.opsForValue().set(votedKey(userId, electionId), "1", settings.votedTtl(), TimeUnit.SECONDS);
        } catch (DataAccessException e) {
            log.warn("Redis voted-flag write failed: {}", e.getMessage());
        }
    }

    public Optional<UUID> findIdempotentVoteId(UUID userId, String idempotencyKey) {
        try {
            String voteId = redisTemplate.opsForValue().get(idempotencyKey(userId, idempotencyKey));
            return Optional.ofNullable(voteId).map(UUID::fromString);
        } catch (DataAccessException e) {
            log.warn("Redis idempotency read failed, falling back to database: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public void rememberIdempotentVote(UUID userId, String idempotencyKey, UUID voteId) {
        try {
            redisTemplate.opsForValue().set(idempotencyKey(userId, idempotencyKey), voteId.toString(),
                    settings.idempotencyTtl(), TimeUnit.SECONDS);
        } catch (DataAccessException e) {
            log.warn("Redis idempotency write failed: {}", e.getMessage());
        }
    }

    private String votedKey(UUID userId, UUID electionId) {
        return settings.keyPrefix() + "voted:" + userId + ":" + electionId;
    }

    private String idempotencyKey(UUID userId, String key) {
        return settings.keyPrefix() + "idempotency:" + userId + ":" + key;
    }
}
