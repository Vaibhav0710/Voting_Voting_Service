package com.voting.votingservice.dto;

import com.voting.votingservice.model.enums.VoteStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoteVerificationResponse {
    private UUID voteId;
    private boolean hashValid;
    private VoteStatus status;
    private String storedHash;
    private String computedHash;
    private String message;
}
