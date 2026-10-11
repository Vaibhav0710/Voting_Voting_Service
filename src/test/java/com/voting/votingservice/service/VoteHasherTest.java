package com.voting.votingservice.service;

import com.voting.votingservice.model.Vote;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class VoteHasherTest {

    private final VoteHasher hasher = new VoteHasher();

    private Vote vote(String prevHash) {
        return Vote.builder()
                .userId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .candidateId(UUID.fromString("00000000-0000-0000-0000-000000000002"))
                .electionId(UUID.fromString("00000000-0000-0000-0000-000000000003"))
                .timestamp(LocalDateTime.of(2026, 10, 10, 12, 0, 0))
                .prevHash(prevHash)
                .build();
    }

    @Test
    void sameInput_sameHash() {
        assertThat(hasher.computeHash(vote("GENESIS"))).isEqualTo(hasher.computeHash(vote("GENESIS")));
    }

    @Test
    void nullPrevHash_hashesLikeEmptyString() {
        assertThat(hasher.computeHash(vote(null))).isEqualTo(hasher.computeHash(vote("")));
    }

    @Test
    void matchesFormulaUsedForExistingVotes() {
        // SHA-256("…01" + "…02" + "…03" + epochSecondsUtc + "GENESIS"), the formula stored votes were hashed with
        String expected = hasher.sha256("00000000-0000-0000-0000-000000000001"
                + "00000000-0000-0000-0000-000000000002"
                + "00000000-0000-0000-0000-000000000003"
                + "1791633600"
                + "GENESIS");
        assertThat(hasher.computeHash(vote("GENESIS"))).isEqualTo(expected).hasSize(64);
    }
}
