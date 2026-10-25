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

    // Resilience4j candidateService retry max-attempts configured in application.yml
    private static final int MAX_RETRY_ATTEMPTS = 3;

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
        assertThat(readTimeout).isEqualTo(1000);

        long worstCaseTotalMs = (long) MAX_RETRY_ATTEMPTS * (connectTimeout + readTimeout);
        assertThat(worstCaseTotalMs).isLessThan(GATEWAY_TIMEOUT_MS);
    }
}
