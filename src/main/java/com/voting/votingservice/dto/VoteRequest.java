package com.voting.votingservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO for casting a vote.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoteRequest {

    @NotNull(message = "Candidate ID is required")
    private UUID candidateId;

    @NotNull(message = "Election ID is required")
    private UUID electionId;

    @NotBlank(message = "Idempotency key is required")
    private String idempotencyKey;
}
