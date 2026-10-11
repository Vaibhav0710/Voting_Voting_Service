package com.voting.votingservice.service;

import com.voting.votingservice.model.Vote;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneOffset;
import java.util.HexFormat;

/**
 * Computes a vote's tamper-evident hash:
 * {@code SHA-256(userId + candidateId + electionId + epochSecondsUtc(timestamp) + prevHash)},
 * with a missing prevHash hashed as an empty string.
 */
@Component
public class VoteHasher {

    public String computeHash(Vote vote) {
        String prevHash = vote.getPrevHash() == null ? "" : vote.getPrevHash();
        return sha256(vote.getUserId().toString()
                + vote.getCandidateId()
                + vote.getElectionId()
                + vote.getTimestamp().toEpochSecond(ZoneOffset.UTC)
                + prevHash);
    }

    String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available in this JVM", e);
        }
    }
}
