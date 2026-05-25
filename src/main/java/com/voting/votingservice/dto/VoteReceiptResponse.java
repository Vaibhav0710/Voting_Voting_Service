package com.voting.votingservice.dto;

import com.voting.votingservice.model.enums.VoteStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoteReceiptResponse {
    private UUID voteId;
    private UUID candidateId;
    private UUID electionId;
    private String voteHash;
    private String prevHash;
    private VoteStatus status;
    private LocalDateTime timestamp;
}
