package com.voting.votingservice.controller;

import com.voting.votingservice.dto.*;
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
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Controller for handling voting API requests.
 */
@RestController
@RequestMapping("/api/v1/votes")
@RequiredArgsConstructor
public class VoteController {

    private final VoteServiceInterface voteService;

    @PostMapping
    @PreAuthorize("hasAuthority('VOTER') or hasRole('VOTER')")
    @ResponseStatus(HttpStatus.CREATED)
    public VoteResponse castVote(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody VoteRequest voteRequest
    ) {
        return voteService.castVote(principal.userId(), voteRequest);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<VoteReceiptResponse>> getVoteReceipt(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtPrincipal principal) {
        VoteReceiptResponse response = voteService.getVoteReceipt(id, principal.userId(), principal.role());
        return ResponseEntity.ok(ApiResponse.success(response, "Vote receipt fetched successfully"));
    }

    @GetMapping("/user/{userId}/election/{electionId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> hasUserVoted(
            @PathVariable UUID userId,
            @PathVariable UUID electionId,
            @AuthenticationPrincipal JwtPrincipal principal) {
        
        // Security check: must be ADMIN or checking their own status
        if (!"ROLE_ADMIN".equals(principal.role()) && !userId.equals(principal.userId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("You are not authorized to check vote status for another user"));
        }

        boolean hasVoted = voteService.hasUserVoted(userId, electionId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("hasVoted", hasVoted), "Vote status fetched"));
    }

    @GetMapping("/election/{electionId}")
    @PreAuthorize("hasAuthority('ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<VoteReceiptResponse>>> getElectionVotes(
            @PathVariable UUID electionId, 
            Pageable pageable) {
        Page<VoteReceiptResponse> responses = voteService.getElectionVotes(electionId, pageable);
        return ResponseEntity.ok(ApiResponse.success(responses, "Election votes fetched successfully"));
    }

    @GetMapping("/election/{electionId}/count")
    public ResponseEntity<ApiResponse<VoteCountResponse>> getVoteCounts(
            @PathVariable UUID electionId) {
        VoteCountResponse response = voteService.getVoteCounts(electionId);
        return ResponseEntity.ok(ApiResponse.success(response, "Vote counts fetched successfully"));
    }

    @PostMapping("/verify/{voteId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<VoteVerificationResponse>> verifyVote(
            @PathVariable UUID voteId) {
        VoteVerificationResponse response = voteService.verifyVote(voteId);
        return ResponseEntity.ok(ApiResponse.success(response, "Vote verification completed"));
    }

    @PostMapping("/chain/validate/{electionId}")
    @PreAuthorize("hasAuthority('ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ChainValidationResponse>> validateChain(
            @PathVariable UUID electionId) {
        ChainValidationResponse response = voteService.validateChain(electionId);
        return ResponseEntity.ok(ApiResponse.success(response, "Chain validation completed"));
    }
}
