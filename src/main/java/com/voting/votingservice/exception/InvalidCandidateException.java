package com.voting.votingservice.exception;

/**
 * Exception thrown when the candidate validation fails via candidate-service.
 */
public class InvalidCandidateException extends RuntimeException {
    public InvalidCandidateException(String message) {
        super(message);
    }
}
