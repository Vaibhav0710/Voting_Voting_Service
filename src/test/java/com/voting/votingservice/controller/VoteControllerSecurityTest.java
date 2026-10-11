package com.voting.votingservice.controller;

import com.voting.votingservice.dto.VoteCountResponse;
import com.voting.votingservice.dto.VoteResponse;
import com.voting.votingservice.exception.CandidateServiceUnavailableException;
import com.voting.votingservice.exception.InvalidCandidateException;
import com.voting.votingservice.security.JwtService;
import com.voting.votingservice.security.SecurityConfig;
import com.voting.votingservice.service.VoteServiceInterface;
import com.voting.votingservice.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VoteController.class)
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = "jwt.secret=" + TestTokens.SECRET)
class VoteControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private VoteServiceInterface voteService;

    private final UUID voterId = UUID.randomUUID();
    private final UUID electionId = UUID.randomUUID();

    private MockHttpServletRequestBuilder castVote(String role) {
        MockHttpServletRequestBuilder request = post("/api/v1/votes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"candidateId\":\"" + UUID.randomUUID() + "\",\"electionId\":\"" + electionId
                        + "\",\"idempotencyKey\":\"k-1\"}");
        return role == null ? request : request.header("Authorization", TestTokens.bearer(voterId, role));
    }

    @Test
    void castVote_noToken_401() throws Exception {
        mockMvc.perform(castVote(null)).andExpect(status().isUnauthorized());
    }

    @Test
    void castVote_adminRole_403() throws Exception {
        mockMvc.perform(castVote("ROLE_ADMIN"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void castVote_voter_201() throws Exception {
        when(voteService.castVote(eq(voterId), any()))
                .thenReturn(VoteResponse.builder().voteId(UUID.randomUUID()).voteHash("h").build());

        mockMvc.perform(castVote("ROLE_VOTER"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.voteHash").value("h"));
    }

    @Test
    void castVote_idempotentReplay_200() throws Exception {
        when(voteService.castVote(eq(voterId), any()))
                .thenReturn(VoteResponse.builder().voteHash("h").replay(true).build());

        mockMvc.perform(castVote("ROLE_VOTER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replay").value(true));
    }

    @Test
    void castVote_missingFields_400() throws Exception {
        mockMvc.perform(post("/api/v1/votes")
                        .header("Authorization", TestTokens.bearer(voterId, "ROLE_VOTER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.candidateId").exists());
    }

    @Test
    void castVote_invalidCandidate_400() throws Exception {
        when(voteService.castVote(eq(voterId), any())).thenThrow(new InvalidCandidateException("Candidate is WITHDRAWN"));

        mockMvc.perform(castVote("ROLE_VOTER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Candidate is WITHDRAWN"));
    }

    @Test
    void castVote_candidateServiceDown_503() throws Exception {
        when(voteService.castVote(eq(voterId), any())).thenThrow(new CandidateServiceUnavailableException("down"));

        mockMvc.perform(castVote("ROLE_VOTER")).andExpect(status().isServiceUnavailable());
    }

    @Test
    void getElectionVotes_voter_403() throws Exception {
        mockMvc.perform(get("/api/v1/votes/election/{id}", electionId)
                        .header("Authorization", TestTokens.bearer(voterId, "ROLE_VOTER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void getElectionVotes_admin_200() throws Exception {
        when(voteService.getElectionVotes(eq(electionId), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/votes/election/{id}", electionId)
                        .header("Authorization", TestTokens.bearer(UUID.randomUUID(), "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    void hasUserVoted_ownStatus_200() throws Exception {
        when(voteService.hasUserVoted(voterId, electionId)).thenReturn(true);

        mockMvc.perform(get("/api/v1/votes/user/{userId}/election/{electionId}", voterId, electionId)
                        .header("Authorization", TestTokens.bearer(voterId, "ROLE_VOTER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasVoted").value(true));
    }

    @Test
    void hasUserVoted_otherUsersStatusAsVoter_403() throws Exception {
        mockMvc.perform(get("/api/v1/votes/user/{userId}/election/{electionId}", UUID.randomUUID(), electionId)
                        .header("Authorization", TestTokens.bearer(voterId, "ROLE_VOTER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void chainValidation_voter_403() throws Exception {
        mockMvc.perform(post("/api/v1/votes/chain/validate/{id}", electionId)
                        .header("Authorization", TestTokens.bearer(voterId, "ROLE_VOTER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void voteCounts_arePublic() throws Exception {
        when(voteService.getVoteCounts(electionId))
                .thenReturn(VoteCountResponse.builder().electionId(electionId).candidateVoteCounts(List.of()).build());

        mockMvc.perform(get("/api/v1/votes/election/{id}/count", electionId)).andExpect(status().isOk());
    }

    @Test
    void unexpectedError_500_doesNotLeakMessage() throws Exception {
        when(voteService.castVote(eq(voterId), any())).thenThrow(new IllegalStateException("db password=secret"));

        mockMvc.perform(castVote("ROLE_VOTER"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(not(containsString("secret"))));
    }
}
