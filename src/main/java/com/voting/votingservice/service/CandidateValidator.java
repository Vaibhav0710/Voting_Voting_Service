package com.voting.votingservice.service;

import com.voting.votingservice.client.CandidateApiResponse;
import com.voting.votingservice.client.CandidateClient;
import com.voting.votingservice.client.CandidateValidation;
import com.voting.votingservice.exception.CandidateServiceUnavailableException;
import com.voting.votingservice.exception.InvalidCandidateException;
import feign.FeignException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Checks with the Candidate Service that a candidate can receive votes in an election.
 * <p>
 * Calls are wrapped in a retry, a circuit breaker and a bulkhead ({@code candidateService} instance).
 * When the Candidate Service is unavailable, the last known status from {@link CandidateStatusCache}
 * decides; with nothing cached the vote is refused with 503 rather than accepted blindly.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CandidateValidator {

    static final String CANDIDATE_SERVICE = "candidateService";
    private static final String GENERIC_REJECTION = "Candidate is invalid or not active for this election.";

    private final CandidateClient candidateClient;
    private final CandidateStatusCache statusCache;

    // Aspect order is Retry(CircuitBreaker(Bulkhead(call))). The fallback sits on the outermost Retry so
    // transient failures are retried first and the cache is consulted only once retries are exhausted.
    @Retry(name = CANDIDATE_SERVICE, fallbackMethod = "validateFromCache")
    @CircuitBreaker(name = CANDIDATE_SERVICE)
    @Bulkhead(name = CANDIDATE_SERVICE)
    public void ensureCandidateValid(UUID candidateId, UUID electionId) {
        CandidateValidation validation = fetchValidation(candidateId, electionId);
        if (validation == null || !validation.valid()) {
            throw new InvalidCandidateException(rejectionReason(validation));
        }
        statusCache.put(candidateId, electionId, "ACTIVE");
    }

    /**
     * Fallback for {@link #ensureCandidateValid}: Resilience4j calls it for every failure, so a
     * genuine "invalid candidate" answer is rethrown untouched.
     */
    void validateFromCache(UUID candidateId, UUID electionId, Throwable cause) {
        if (cause instanceof InvalidCandidateException invalid) {
            throw invalid;
        }
        log.warn("Candidate Service unavailable ({}); using cached status for candidate {}",
                cause.getClass().getSimpleName(), candidateId);
        CandidateStatusCache.Entry entry = statusCache.find(candidateId)
                .orElseThrow(() -> new CandidateServiceUnavailableException(
                        "Candidate Service is unavailable and candidate status is unknown. Please retry shortly."));
        if (!entry.isActiveIn(electionId)) {
            throw new InvalidCandidateException(GENERIC_REJECTION);
        }
    }

    private CandidateValidation fetchValidation(UUID candidateId, UUID electionId) {
        try {
            CandidateApiResponse<CandidateValidation> response = candidateClient.validateCandidate(candidateId, electionId);
            return response == null ? null : response.data();
        } catch (FeignException e) {
            if (isClientError(e)) {
                throw new InvalidCandidateException(GENERIC_REJECTION);
            }
            throw e;
        }
    }

    private static boolean isClientError(FeignException e) {
        return e.status() >= 400 && e.status() < 500;
    }

    private static String rejectionReason(CandidateValidation validation) {
        if (validation == null || validation.message() == null) {
            return GENERIC_REJECTION;
        }
        return validation.message();
    }
}
