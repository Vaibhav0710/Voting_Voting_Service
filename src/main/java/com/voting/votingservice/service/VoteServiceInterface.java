package com.voting.votingservice.service;

import com.voting.votingservice.dto.ChainValidationResponse;
import com.voting.votingservice.dto.VoteCountResponse;
import com.voting.votingservice.dto.VoteReceiptResponse;
import com.voting.votingservice.dto.VoteRequest;
import com.voting.votingservice.dto.VoteResponse;
import com.voting.votingservice.dto.VoteVerificationResponse;
import com.voting.votingservice.security.JwtPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface VoteServiceInterface {

    VoteResponse castVote(UUID userId, VoteRequest request);

    VoteReceiptResponse getVoteReceipt(UUID voteExternalId, JwtPrincipal requester);

    boolean hasUserVoted(UUID userId, UUID electionId);

    Page<VoteReceiptResponse> getElectionVotes(UUID electionId, Pageable pageable);

    VoteCountResponse getVoteCounts(UUID electionId);

    VoteVerificationResponse verifyVote(UUID voteExternalId, JwtPrincipal requester);

    ChainValidationResponse validateChain(UUID electionId);
}
