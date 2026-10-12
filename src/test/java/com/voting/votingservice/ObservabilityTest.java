package com.voting.votingservice;

import com.voting.votingservice.service.CandidateStatusCache;
import com.voting.votingservice.service.VoteRedisStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Metrics (including the vote outcome counter) are scrapeable by Prometheus and health probes are exposed.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@AutoConfigureObservability
class ObservabilityTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private VoteRedisStore redisStore;
    @MockBean private CandidateStatusCache candidateStatusCache;

    @Test
    void prometheusEndpoint_exposesServiceAndVoteMetrics() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("application=\"voting-service\"")))
                .andExpect(content().string(containsString("votes_cast_total{")))
                .andExpect(content().string(containsString("outcome=\"duplicate\"")));
    }

    @Test
    void readinessProbe_isExposed() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }
}
