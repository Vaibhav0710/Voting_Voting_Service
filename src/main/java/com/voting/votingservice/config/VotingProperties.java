package com.voting.votingservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Typed view of the {@code voting.*} settings.
 *
 * @param redis key prefix and TTLs (seconds) for the voted flag and idempotency entries
 * @param chain whether each vote links to the previous vote's hash in its election
 */
@ConfigurationProperties(prefix = "voting")
public record VotingProperties(
        @DefaultValue Redis redis,
        @DefaultValue Chain chain
) {
    public record Redis(
            @DefaultValue("vot:") String keyPrefix,
            @DefaultValue("86400") long votedTtl,
            @DefaultValue("86400") long idempotencyTtl
    ) {
    }

    public record Chain(@DefaultValue("true") boolean enabled) {
    }
}
