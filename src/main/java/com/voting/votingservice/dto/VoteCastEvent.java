package com.voting.votingservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Kafka event payload published when a vote is successfully cast.
 * <p>
 * Decoupled from {@link VoteResponse} (API response contract) to allow
 * independent evolution of the event schema and REST response.
 * <p>
 * Consumers: Result Service (vote tallying), future Audit Service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoteCastEvent {

    private UUID voteId;
    private UUID userId;
    private UUID candidateId;
    private UUID electionId;
    private String voteHash;
    private LocalDateTime timestamp;
}
