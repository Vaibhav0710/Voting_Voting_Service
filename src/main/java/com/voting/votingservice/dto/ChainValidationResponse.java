package com.voting.votingservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChainValidationResponse {
    private UUID electionId;
    private boolean chainValid;
    private long totalVotesChecked;
    private int brokenLinks;
    private String message;
}
