package com.voting.votingservice.mapper;

import com.voting.votingservice.dto.VoteReceiptResponse;
import com.voting.votingservice.dto.VoteResponse;
import com.voting.votingservice.model.Vote;
import org.springframework.stereotype.Component;

@Component
public class VoteMapper {

    public VoteResponse toVoteResponse(Vote vote) {
        return buildResponse(vote, "Vote successfully cast!", false);
    }

    public VoteResponse toReplayResponse(Vote vote) {
        return buildResponse(vote, "Vote already recorded (idempotent replay).", true);
    }

    public VoteReceiptResponse toReceiptResponse(Vote vote) {
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

    private static VoteResponse buildResponse(Vote vote, String message, boolean replay) {
        return VoteResponse.builder()
                .voteId(vote.getExternalId())
                .voteHash(vote.getVoteHash())
                .timestamp(vote.getTimestamp())
                .message(message)
                .replay(replay)
                .build();
    }
}
