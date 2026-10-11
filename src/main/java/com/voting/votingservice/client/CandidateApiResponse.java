package com.voting.votingservice.client;

/**
 * Candidate Service response envelope ({@code ApiResponse} on that side).
 */
public record CandidateApiResponse<T>(
        boolean success,
        String message,
        T data
) {
}
