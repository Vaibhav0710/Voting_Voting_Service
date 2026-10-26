package com.voting.votingservice.config;

import com.voting.votingservice.service.CandidateStatusCache;
import com.voting.votingservice.service.VoteRedisStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.openfeign.FeignClientProperties;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class FeignClientTimeoutTest {

    // Gateway timelimiter timeout set in api-gateway/src/main/resources/application.yml
    // under resilience4j.timelimiter.configs.default.timeout-duration: 5s
    private static final long GATEWAY_TIMEOUT_MS = 5000L;

    // Minimum required headroom under the gateway timeout to ensure the vote's own DB
    // write and per-election chain lock complete within the same gateway time budget
    private static final long MIN_HEADROOM_MS = 500L;

    // Resilience4j candidateService retry max-attempts configured in application.yml
    // under resilience4j.retry.instances.candidateService.max-attempts: 3
    private static final int MAX_RETRY_ATTEMPTS = 3;

    // Resilience4j candidateService retry wait-duration configured in application.yml
    // under resilience4j.retry.instances.candidateService.wait-duration: 200ms
    private static final long RETRY_WAIT_DURATION_MS = 200L;

    @MockBean private VoteRedisStore redisStore;
    @MockBean private CandidateStatusCache statusCache;

    @Autowired(required = false)
    private FeignClientProperties feignClientProperties;

    @Test
    void feignDefaultTimeouts_configuredToFailFastUnderGatewayTimeout() {
        assertThat(feignClientProperties).isNotNull();
        assertThat(feignClientProperties.getConfig()).containsKey("default");

        FeignClientProperties.FeignClientConfiguration defaultConfig =
                feignClientProperties.getConfig().get("default");

        int connectTimeout = defaultConfig.getConnectTimeout();
        int readTimeout = defaultConfig.getReadTimeout();

        assertThat(connectTimeout).isEqualTo(500);
        assertThat(readTimeout).isEqualTo(700);

        long worstCaseTotalMs = (long) MAX_RETRY_ATTEMPTS * (connectTimeout + readTimeout)
                + (long) (MAX_RETRY_ATTEMPTS - 1) * RETRY_WAIT_DURATION_MS;
        assertThat(worstCaseTotalMs).isLessThan(GATEWAY_TIMEOUT_MS);
        assertThat(GATEWAY_TIMEOUT_MS - worstCaseTotalMs).isGreaterThanOrEqualTo(MIN_HEADROOM_MS);
    }
}
