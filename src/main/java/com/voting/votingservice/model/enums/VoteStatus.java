package com.voting.votingservice.model.enums;

/**
 * Vote lifecycle states for future audit trail expansion.
 */
public enum VoteStatus {

    /** Vote successfully cast and recorded. */
    CAST,

    /** Vote hash has been independently verified. */
    VERIFIED,

    /** Vote is under dispute (flagged for review). */
    DISPUTED
}
