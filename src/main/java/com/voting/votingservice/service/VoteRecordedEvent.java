package com.voting.votingservice.service;

import com.voting.votingservice.dto.VoteCastEvent;

/**
 * In-process event raised inside the vote transaction; handled after commit by {@link VoteCastSideEffects}.
 */
public record VoteRecordedEvent(VoteCastEvent voteCast, String idempotencyKey) {
}
