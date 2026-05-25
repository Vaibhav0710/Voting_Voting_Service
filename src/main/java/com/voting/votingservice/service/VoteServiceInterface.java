package com.voting.votingservice.service;

import com.voting.votingservice.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface VoteServiceInterface {
    VoteResponse castVote(UUID userId, VoteRequest request);
    
    VoteReceiptResponse getVoteReceipt(UUID voteExternalId, UUID requestingUserId, String role);
    
    boolean hasUserVoted(UUID userId, UUID electionId);
    
    Page<VoteReceiptResponse> getElectionVotes(UUID electionId, Pageable pageable);
    
    VoteCountResponse getVoteCounts(UUID electionId);
    
    VoteVerificationResponse verifyVote(UUID voteExternalId);
    
    ChainValidationResponse validateChain(UUID electionId);
}
