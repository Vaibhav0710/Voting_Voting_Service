package com.voting.votingservice.mapper;

import com.voting.votingservice.dto.VoteReceiptResponse;
import com.voting.votingservice.dto.VoteResponse;
import com.voting.votingservice.model.Vote;
import org.springframework.stereotype.Component;

@Component
public class VoteMapper {

    public VoteResponse toVoteResponse(Vote vote) {
        if (vote == null) {
            return null;
        }
        
        return VoteResponse.builder()
                .voteId(vote.getExternalId())
                .voteHash(vote.getVoteHash())
                .timestamp(vote.getTimestamp())
                .message("Vote successfully cast!")
                .build();
    }

    public VoteReceiptResponse toReceiptResponse(Vote vote) {
        if (vote == null) {
            return null;
        }
        
        return VoteReceiptResponse.builder()
                .voteId(vote.getExternalId())
                .candidateId(vote.getCandidateId())
                .electionId(vote.getElectionId())
                .voteHash(vote.getVoteHash())
                .prevHash(vote.getPrevHash())
                .status(vote.getStatus())
                .timestamp(vote.getTimestamp())
                .build();
    }
}
