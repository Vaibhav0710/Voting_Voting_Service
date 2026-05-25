package com.voting.votingservice.exception;

public class ChainValidationException extends RuntimeException {
    public ChainValidationException(String message) {
        super(message);
    }
}
