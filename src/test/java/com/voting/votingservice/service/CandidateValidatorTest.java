package com.voting.votingservice.service;

import com.voting.votingservice.client.CandidateApiResponse;
import com.voting.votingservice.client.CandidateClient;
import com.voting.votingservice.client.CandidateValidation;
import com.voting.votingservice.exception.CandidateServiceUnavailableException;
import com.voting.votingservice.exception.InvalidCandidateException;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CandidateValidatorTest {

    @Mock private CandidateClient candidateClient;
    @Mock private CandidateStatusCache statusCache;
    @InjectMocks private CandidateValidator validator;

    private final UUID candidateId = UUID.randomUUID();
    private final UUID electionId = UUID.randomUUID();

    private static FeignException feignError(int status) {
        Request request = Request.create(Request.HttpMethod.GET, "/api/v1/candidates/x/validate",
                Map.of(), null, StandardCharsets.UTF_8, null);
        return FeignException.errorStatus("validateCandidate",
                feign.Response.builder().status(status).reason("x").request(request).headers(Map.of()).build());
    }

    private CandidateApiResponse<CandidateValidation> response(boolean valid, String message) {
        return new CandidateApiResponse<>(true, "Validation completed",
                new CandidateValidation(candidateId, electionId, valid, "ACTIVE", message));
    }

    @Test
    void validCandidate_passes() {
        when(candidateClient.validateCandidate(candidateId, electionId)).thenReturn(response(true, "Valid"));

        assertThatCode(() -> validator.ensureCandidateValid(candidateId, electionId)).doesNotThrowAnyException();
    }

    @Test
    void candidateNotValid_throwsInvalidCandidateWithReason() {
        when(candidateClient.validateCandidate(candidateId, electionId))
                .thenReturn(response(false, "Candidate is DISQUALIFIED"));

        assertThatThrownBy(() -> validator.ensureCandidateValid(candidateId, electionId))
                .isInstanceOf(InvalidCandidateException.class)
                .hasMessage("Candidate is DISQUALIFIED");
    }

    @Test
    void emptyResponse_throwsInvalidCandidate() {
        when(candidateClient.validateCandidate(candidateId, electionId))
                .thenReturn(new CandidateApiResponse<>(false, "x", null));

        assertThatThrownBy(() -> validator.ensureCandidateValid(candidateId, electionId))
                .isInstanceOf(InvalidCandidateException.class);
    }

    @Test
    void candidateServiceReturns4xx_throwsInvalidCandidate() {
        when(candidateClient.validateCandidate(candidateId, electionId)).thenThrow(feignError(400));

        assertThatThrownBy(() -> validator.ensureCandidateValid(candidateId, electionId))
                .isInstanceOf(InvalidCandidateException.class);
    }

    @Test
    void fallback_cachedActiveCandidateOfSameElection_passes() {
        when(statusCache.find(candidateId))
                .thenReturn(java.util.Optional.of(new CandidateStatusCache.Entry(electionId, "ACTIVE")));

        assertThatCode(() -> validator.validateFromCache(candidateId, electionId, feignError(503)))
                .doesNotThrowAnyException();
    }

    @Test
    void fallback_cachedDisqualifiedCandidate_throwsInvalidCandidate() {
        when(statusCache.find(candidateId))
                .thenReturn(java.util.Optional.of(new CandidateStatusCache.Entry(electionId, "DISQUALIFIED")));

        assertThatThrownBy(() -> validator.validateFromCache(candidateId, electionId, feignError(503)))
                .isInstanceOf(InvalidCandidateException.class);
    }

    @Test
    void fallback_nothingCached_throwsServiceUnavailable() {
        when(statusCache.find(candidateId)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> validator.validateFromCache(candidateId, electionId, feignError(503)))
                .isInstanceOf(CandidateServiceUnavailableException.class);
    }

    @Test
    void fallback_invalidCandidateFromService_isRethrownNotMasked() {
        InvalidCandidateException original = new InvalidCandidateException("Candidate is WITHDRAWN");

        assertThatThrownBy(() -> validator.validateFromCache(candidateId, electionId, original))
                .isSameAs(original);
    }
}
