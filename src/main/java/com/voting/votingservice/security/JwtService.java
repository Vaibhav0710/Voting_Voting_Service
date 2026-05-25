package com.voting.votingservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Optional;
import java.util.UUID;

/**
 * Service to validate and extract claims from the JWT token.
 */
@Slf4j
@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;

    public Optional<JwtPrincipal> getPrincipalFromToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            String subject = claims.getSubject();
            String role = claims.get("role", String.class);
            
            if (subject != null && role != null) {
                return Optional.of(new JwtPrincipal(UUID.fromString(subject), role));
            }
        } catch (Exception e) {
            log.error("Failed to extract principal from token: {}", e.getMessage());
        }
        return Optional.empty();
    }

    private Claims extractAllClaims(String token) {
        return Jwts
                .parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
