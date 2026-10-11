package com.voting.votingservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Validates JWTs issued by the User Service and extracts the caller's identity.
 * <p>
 * Token contract (set by User Service): subject = username, {@code userId} claim = UUID,
 * {@code role} claim = ROLE_VOTER / ROLE_ADMIN. Signature and expiry are checked by JJWT.
 */
@Slf4j
@Service
public class JwtService {

    private final SecretKey signingKey;

    public JwtService(@Value("${jwt.secret}") String base64Secret) {
        this.signingKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(base64Secret));
    }

    public Optional<JwtPrincipal> getPrincipalFromToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String userId = claims.get("userId", String.class);
            String role = claims.get("role", String.class);
            if (userId == null || role == null) {
                log.warn("JWT is missing the userId or role claim");
                return Optional.empty();
            }
            return Optional.of(new JwtPrincipal(UUID.fromString(userId), claims.getSubject(), role));
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Rejected JWT: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
