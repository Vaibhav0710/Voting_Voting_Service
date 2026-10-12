package com.voting.votingservice.service;

import com.voting.votingservice.audit.AuditLogger;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Records what happened to each vote attempt: the {@code votes.cast} counter (tag {@code outcome}, exported to
 * Prometheus as {@code votes_cast_total}) and an audit line.
 * <p>
 * Ballot privacy: audit lines name the voter and the vote or election, never the candidate chosen.
 */
@Component
public class VoteOutcomeRecorder {

    public enum Outcome { SUCCESS, REPLAY, DUPLICATE, INVALID_CANDIDATE, CANDIDATE_SERVICE_UNAVAILABLE }

    static final String METRIC = "votes.cast";
    private static final String VOTE_CAST = "VOTE_CAST";

    private final AuditLogger auditLogger;
    private final Map<Outcome, Counter> counters = new EnumMap<>(Outcome.class);

    public VoteOutcomeRecorder(AuditLogger auditLogger, MeterRegistry meterRegistry) {
        this.auditLogger = auditLogger;
        for (Outcome outcome : Outcome.values()) {
            counters.put(outcome, Counter.builder(METRIC)
                    .description("Vote casting attempts by outcome")
                    .tag("outcome", outcome.name().toLowerCase(Locale.ROOT))
                    .register(meterRegistry));
        }
    }

    public void cast(UUID userId, UUID voteId) {
        counters.get(Outcome.SUCCESS).increment();
        auditLogger.record(VOTE_CAST, user(userId), "vote:" + voteId, Outcome.SUCCESS.name());
    }

    public void replayed(UUID userId, UUID voteId) {
        counters.get(Outcome.REPLAY).increment();
        auditLogger.record(VOTE_CAST, user(userId), "vote:" + voteId, Outcome.REPLAY.name());
    }

    public void rejected(UUID userId, UUID electionId, Outcome outcome) {
        counters.get(outcome).increment();
        auditLogger.record(VOTE_CAST, user(userId), "election:" + electionId, outcome.name());
    }

    public void verified(UUID voteId, boolean hashValid) {
        auditLogger.recordForCurrentUser("VOTE_VERIFIED", "vote:" + voteId, hashValid ? "VALID" : "TAMPERED");
    }

    public void chainValidated(UUID electionId, boolean chainValid) {
        auditLogger.recordForCurrentUser("CHAIN_VALIDATED", "election:" + electionId, chainValid ? "VALID" : "BROKEN");
    }

    private static String user(UUID userId) {
        return "user:" + userId;
    }
}
