package com.voting.votingservice.support;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * Builds JWTs shaped like the User Service's, signed with the test secret.
 */
public final class TestTokens {

    public static final String SECRET =
            "dGVzdC1zZWNyZXQta2V5LXRoYXQtaXMtbG9uZy1lbm91Z2gtZm9yLWhtYWMtc2hhMjU2LWFsZ29yaXRobQ==";

    private TestTokens() {
    }

    public static String bearer(UUID userId, String role) {
        Date now = new Date();
        String token = Jwts.builder()
                .subject("user-" + userId)
                .claim("userId", userId.toString())
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 60_000))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET)), Jwts.SIG.HS256)
                .compact();
        return "Bearer " + token;
    }
}
