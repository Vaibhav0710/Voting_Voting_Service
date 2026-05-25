package com.voting.votingservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoteCountResponse {
    private UUID electionId;
    private long totalVotes;
    private List<CandidateVoteCount> candidateVoteCounts;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CandidateVoteCount {
        private UUID candidateId;
        private long voteCount;
    }
}
