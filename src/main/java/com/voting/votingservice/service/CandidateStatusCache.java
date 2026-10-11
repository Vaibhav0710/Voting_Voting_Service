package com.voting.votingservice.service;

import com.voting.votingservice.config.VotingProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Local copy of each candidate's election and status, kept in Redis.
 * <p>
 * Filled from Candidate Service Kafka events and from successful validations. Used only as a
 * fallback when the Candidate Service is unreachable, so voting can continue for known ACTIVE candidates.
 * Redis failures degrade to "nothing cached".
 */
@Slf4j
@Component
public class CandidateStatusCache {

    private static final String ELECTION_FIELD = "electionId";
    private static final String STATUS_FIELD = "status";

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;

    public CandidateStatusCache(StringRedisTemplate redisTemplate, VotingProperties properties) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = properties.redis().keyPrefix() + "candidate:";
    }

    public record Entry(UUID electionId, String status) {
        public boolean isActiveIn(UUID election) {
            return "ACTIVE".equals(status) && electionId.equals(election);
        }
    }

    public Optional<Entry> find(UUID candidateId) {
        try {
            Map<Object, Object> fields = redisTemplate.opsForHash().entries(keyPrefix + candidateId);
            Object election = fields.get(ELECTION_FIELD);
            Object status = fields.get(STATUS_FIELD);
            if (election == null || status == null) {
                return Optional.empty();
            }
            return Optional.of(new Entry(UUID.fromString(election.toString()), status.toString()));
        } catch (DataAccessException e) {
            log.warn("Candidate status cache read failed for {}: {}", candidateId, e.getMessage());
            return Optional.empty();
        }
    }

    public void put(UUID candidateId, UUID electionId, String status) {
        try {
            redisTemplate.opsForHash().putAll(keyPrefix + candidateId,
                    Map.of(ELECTION_FIELD, electionId.toString(), STATUS_FIELD, status));
        } catch (DataAccessException e) {
            log.warn("Candidate status cache write failed for {}: {}", candidateId, e.getMessage());
        }
    }
}
