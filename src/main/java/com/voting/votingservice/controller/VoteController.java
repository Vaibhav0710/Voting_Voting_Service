package com.voting.votingservice.controller;

import com.voting.votingservice.dto.ApiResponse;
import com.voting.votingservice.dto.ChainValidationResponse;
import com.voting.votingservice.dto.VoteCountResponse;
import com.voting.votingservice.dto.VoteReceiptResponse;
import com.voting.votingservice.dto.VoteRequest;
import com.voting.votingservice.dto.VoteResponse;
import com.voting.votingservice.dto.VoteVerificationResponse;
import com.voting.votingservice.security.JwtPrincipal;
import com.voting.votingservice.service.VoteServiceInterface;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Controller for handling voting API requests. Identity always comes from the verified JWT
 * ({@link JwtPrincipal}); role rules are declared per endpoint with {@code @PreAuthorize}.
 */
@RestController
@RequestMapping("/api/v1/votes")
@RequiredArgsConstructor
public class VoteController {

    private final VoteServiceInterface voteService;

    /** 201 for a new vote, 200 when the idempotency key replays an earlier vote. */
    @PostMapping
    @PreAuthorize("hasRole('VOTER')")
    public ResponseEntity<ApiResponse<VoteResponse>> castVote(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody VoteRequest voteRequest) {
        VoteResponse response = voteService.castVote(principal.userId(), voteRequest);
        HttpStatus status = response.isReplay() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(ApiResponse.success(response, response.getMessage()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<VoteReceiptResponse>> getVoteReceipt(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtPrincipal principal) {
        VoteReceiptResponse response = voteService.getVoteReceipt(id, principal);
        return ResponseEntity.ok(ApiResponse.success(response, "Vote receipt fetched successfully"));
    }

    @GetMapping("/user/{userId}/election/{electionId}")
    @PreAuthorize("hasRole('ADMIN') or #userId == principal.userId()")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> hasUserVoted(
            @PathVariable UUID userId,
            @PathVariable UUID electionId) {
        boolean hasVoted = voteService.hasUserVoted(userId, electionId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("hasVoted", hasVoted), "Vote status fetched"));
    }

    @GetMapping("/election/{electionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<VoteReceiptResponse>>> getElectionVotes(
            @PathVariable UUID electionId,
            Pageable pageable) {
        Page<VoteReceiptResponse> responses = voteService.getElectionVotes(electionId, pageable);
        return ResponseEntity.ok(ApiResponse.success(responses, "Election votes fetched successfully"));
    }

    @GetMapping("/election/{electionId}/count")
    public ResponseEntity<ApiResponse<VoteCountResponse>> getVoteCounts(@PathVariable UUID electionId) {
        VoteCountResponse response = voteService.getVoteCounts(electionId);
        return ResponseEntity.ok(ApiResponse.success(response, "Vote counts fetched successfully"));
    }

    @PostMapping("/verify/{voteId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<VoteVerificationResponse>> verifyVote(
            @PathVariable UUID voteId,
            @AuthenticationPrincipal JwtPrincipal principal) {
        VoteVerificationResponse response = voteService.verifyVote(voteId, principal);
        return ResponseEntity.ok(ApiResponse.success(response, "Vote verification completed"));
    }

    @PostMapping("/chain/validate/{electionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ChainValidationResponse>> validateChain(@PathVariable UUID electionId) {
        ChainValidationResponse response = voteService.validateChain(electionId);
        return ResponseEntity.ok(ApiResponse.success(response, "Chain validation completed"));
    }
}
