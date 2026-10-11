package com.voting.votingservice.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET =
            "dGVzdC1zZWNyZXQta2V5LXRoYXQtaXMtbG9uZy1lbm91Z2gtZm9yLWhtYWMtc2hhMjU2LWFsZ29yaXRobQ==";
    private static final String OTHER_SECRET =
            "b3RoZXItc2VjcmV0LWtleS10aGF0LWlzLWxvbmctZW5vdWdoLWZvci1obWFjLXNoYTI1Ni1hbGdvcml0aG0=";

    private final JwtService jwtService = new JwtService(SECRET);

    @Test
    void validToken_returnsPrincipalFromUserIdClaim() {
        UUID userId = UUID.randomUUID();
        String token = token(SECRET, Map.of("userId", userId.toString(), "role", "ROLE_VOTER"), 60_000);

        Optional<JwtPrincipal> principal = jwtService.getPrincipalFromToken(token);

        assertThat(principal).isPresent();
        assertThat(principal.get().userId()).isEqualTo(userId);
        assertThat(principal.get().username()).isEqualTo("vaibhav");
        assertThat(principal.get().role()).isEqualTo("ROLE_VOTER");
    }

    @Test
    void expiredToken_returnsEmpty() {
        String token = token(SECRET, Map.of("userId", UUID.randomUUID().toString(), "role", "ROLE_VOTER"), -1_000);

        assertThat(jwtService.getPrincipalFromToken(token)).isEmpty();
    }

    @Test
    void tokenSignedWithOtherKey_returnsEmpty() {
        String token = token(OTHER_SECRET, Map.of("userId", UUID.randomUUID().toString(), "role", "ROLE_VOTER"), 60_000);

        assertThat(jwtService.getPrincipalFromToken(token)).isEmpty();
    }

    @Test
    void tokenWithoutUserId_returnsEmpty() {
        String token = token(SECRET, Map.of("role", "ROLE_VOTER"), 60_000);

        assertThat(jwtService.getPrincipalFromToken(token)).isEmpty();
    }

    private static String token(String base64Secret, Map<String, Object> claims, long ttlMs) {
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(base64Secret));
        Date now = new Date();
        return Jwts.builder()
                .subject("vaibhav")
                .claims(claims)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMs))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}
