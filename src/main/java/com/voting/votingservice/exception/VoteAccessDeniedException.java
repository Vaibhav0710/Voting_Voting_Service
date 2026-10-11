package com.voting.votingservice.exception;

/**
 * Thrown when a caller tries to read or verify a vote that is not theirs (and they are not ADMIN).
 */
public class VoteAccessDeniedException extends RuntimeException {
    public VoteAccessDeniedException(String message) {
        super(message);
    }
}
