package com.voting.votingservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO returned after a vote is successfully cast.
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
}
