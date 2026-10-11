package com.voting.votingservice.service;

import com.voting.votingservice.client.CandidateClient;
import com.voting.votingservice.exception.CandidateServiceUnavailableException;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Checks the Resilience4j annotations on {@link CandidateValidator} are active through the Spring proxy:
 * a 503 is retried, then the cached-status fallback decides.
 */
@SpringBootTest
@ActiveProfiles("test")
class CandidateValidatorResilienceIntegrationTest {

    @Autowired private CandidateValidator validator;
    @MockBean private CandidateClient candidateClient;
    @MockBean private CandidateStatusCache statusCache;
    @MockBean private VoteRedisStore redisStore;

    private final UUID candidateId = UUID.randomUUID();
    private final UUID electionId = UUID.randomUUID();

    private static FeignException serviceUnavailable() {
        Request request = Request.create(Request.HttpMethod.GET, "/validate", Map.of(), null,
                StandardCharsets.UTF_8, null);
        return FeignException.errorStatus("validateCandidate",
                Response.builder().status(503).reason("down").request(request).headers(Map.of()).build());
    }

    @Test
    void candidateServiceDown_retriesThreeTimesThenUsesCachedActiveStatus() {
        when(candidateClient.validateCandidate(any(), any())).thenThrow(serviceUnavailable());
        when(statusCache.find(candidateId))
                .thenReturn(Optional.of(new CandidateStatusCache.Entry(electionId, "ACTIVE")));

        assertThatCode(() -> validator.ensureCandidateValid(candidateId, electionId)).doesNotThrowAnyException();
        verify(candidateClient, times(3)).validateCandidate(candidateId, electionId);
    }

    @Test
    void candidateServiceDownAndNothingCached_returnsUnavailable() {
        when(candidateClient.validateCandidate(any(), any())).thenThrow(serviceUnavailable());
        when(statusCache.find(candidateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> validator.ensureCandidateValid(candidateId, electionId))
                .isInstanceOf(CandidateServiceUnavailableException.class);
    }
}
