package com.voting.votingservice.client;

import java.util.UUID;

/**
 * Result of {@code GET /api/v1/candidates/{id}/validate}. The endpoint always answers 200;
 * {@code valid} says whether the candidate exists, belongs to the election and is ACTIVE.
 */
public record CandidateValidation(
        UUID candidateId,
        UUID electionId,
        boolean valid,
        String currentStatus,
        String message
) {
}
