package com.voting.votingservice.service;

import com.voting.votingservice.audit.AuditLogger;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class VoteOutcomeRecorderTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final AuditLogger auditLogger = mock(AuditLogger.class);
    private final VoteOutcomeRecorder recorder = new VoteOutcomeRecorder(auditLogger, registry);

    private final UUID userId = UUID.randomUUID();
    private final UUID voteId = UUID.randomUUID();
    private final UUID electionId = UUID.randomUUID();

    private double count(String outcome) {
        return registry.get("votes.cast").tag("outcome", outcome).counter().count();
    }

    @Test
    void everyOutcomeCounter_isRegisteredUpFrontAtZero() {
        assertThat(registry.get("votes.cast").counters()).hasSize(VoteOutcomeRecorder.Outcome.values().length);
        assertThat(count("success")).isZero();
    }

    @Test
    void successfulVote_incrementsSuccessAndAuditsVoteNotCandidate() {
        recorder.cast(userId, voteId);

        assertThat(count("success")).isEqualTo(1);
        verify(auditLogger).record("VOTE_CAST", "user:" + userId, "vote:" + voteId, "SUCCESS");
    }

    @Test
    void duplicateVote_isCountedAndAuditedAgainstTheElection() {
        recorder.rejected(userId, electionId, VoteOutcomeRecorder.Outcome.DUPLICATE);

        assertThat(count("duplicate")).isEqualTo(1);
        verify(auditLogger).record("VOTE_CAST", "user:" + userId, "election:" + electionId, "DUPLICATE");
    }

    @Test
    void replay_isCountedSeparatelyFromNewVotes() {
        recorder.replayed(userId, voteId);

        assertThat(count("replay")).isEqualTo(1);
        assertThat(count("success")).isZero();
    }

    @Test
    void verificationAndChainValidation_areAuditedForTheCurrentUser() {
        recorder.verified(voteId, false);
        recorder.chainValidated(electionId, true);

        verify(auditLogger).recordForCurrentUser("VOTE_VERIFIED", "vote:" + voteId, "TAMPERED");
        verify(auditLogger).recordForCurrentUser("CHAIN_VALIDATED", "election:" + electionId, "VALID");
    }

    @Test
    void auditArguments_neverContainACandidate() {
        recorder.cast(userId, voteId);
        recorder.rejected(userId, electionId, VoteOutcomeRecorder.Outcome.INVALID_CANDIDATE);

        ArgumentCaptor<String> args = ArgumentCaptor.forClass(String.class);
        verify(auditLogger, times(2)).record(args.capture(), args.capture(), args.capture(), args.capture());
        assertThat(args.getAllValues()).noneMatch(value -> value.startsWith("candidate:"));
    }
}
