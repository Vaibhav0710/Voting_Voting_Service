package com.voting.votingservice.exception;

/**
 * Thrown when the Candidate Service cannot be reached and no cached candidate status can decide the vote.
 */
public class CandidateServiceUnavailableException extends RuntimeException {
    public CandidateServiceUnavailableException(String message) {
        super(message);
    }
}
