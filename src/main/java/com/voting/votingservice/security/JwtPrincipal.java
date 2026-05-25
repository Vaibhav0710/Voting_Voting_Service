package com.voting.votingservice.security;

import java.util.UUID;

/**
 * Represents the authenticated user extracted from the JWT token.
 */
public record JwtPrincipal(
        UUID userId,
        String role
) {
}
