package com.voting.votingservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/**
 * Feign client for the Candidate Service (resolved through Eureka).
 */
@FeignClient(name = "candidate-service")
public interface CandidateClient {

    @GetMapping("/api/v1/candidates/{id}/validate")
    CandidateApiResponse<CandidateValidation> validateCandidate(
            @PathVariable("id") UUID candidateId,
            @RequestParam("electionId") UUID electionId
    );
}
