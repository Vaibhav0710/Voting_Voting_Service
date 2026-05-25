package com.voting.votingservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/**
 * Feign client for communicating with the Candidate Service.
 */
@FeignClient(name = "candidate-service")
public interface CandidateClient {

    /**
     * Validates if a candidate exists and is active for the given election.
     * Candidate Service returns 200 OK if valid, or 400/404 if invalid.
     */
    @GetMapping("/api/v1/candidates/{id}/validate")
    void validateCandidate(
            @PathVariable("id") UUID candidateId,
            @RequestParam("electionId") UUID electionId
    );
}
