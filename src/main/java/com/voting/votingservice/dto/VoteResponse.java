package com.voting.votingservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Receipt returned when a vote is cast. {@code replay} is true when the same idempotency key
 * was already used by this voter and the original receipt is returned instead of a new vote.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoteResponse {
    private UUID voteId;
    private String voteHash;
    private LocalDateTime timestamp;
    private String message;
    private boolean replay;
}
