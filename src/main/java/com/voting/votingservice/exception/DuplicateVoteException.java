package com.voting.votingservice.exception;

/**
 * Exception thrown when a user attempts to vote more than once in an election.
 */
public class DuplicateVoteException extends RuntimeException {
    public DuplicateVoteException(String message) {
        super(message);
    }
}
