package com.voting.votingservice.dto;

import java.util.UUID;

/**
 * Fields Voting Service reads from Candidate Service events
 * ({@code candidate.created}, {@code candidate.status-changed}, {@code candidate.deleted}).
 */
public record CandidateEvent(UUID id, UUID electionId, String status) {

    public boolean isComplete() {
        return id != null && electionId != null && status != null;
    }
}
