package com.voting.votingservice.security;

import java.util.UUID;

/**
 * Authenticated caller extracted from the JWT.
 */
public record JwtPrincipal(
        UUID userId,
        String username,
        String role
) {
    public boolean isAdmin() {
        return "ROLE_ADMIN".equals(role);
    }
}
