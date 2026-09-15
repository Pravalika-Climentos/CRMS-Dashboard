package com.example.Auth.Security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.example.CRM.Entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class JwtService {
    private final Algorithm algorithm;
    private final JWTVerifier verifier;
    private final Duration accessTokenLifetime;
    private final String issuer;

    public JwtService(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.issuer:climentos-crm}") String issuer,
            @Value("${security.jwt.access-token-minutes:480}") long minutes) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("security.jwt.secret must contain at least 32 characters");
        }
        this.algorithm = Algorithm.HMAC256(secret);
        this.issuer = issuer;
        this.verifier = JWT.require(algorithm).withIssuer(issuer).build();
        this.accessTokenLifetime = Duration.ofMinutes(minutes);
    }

    public IssuedToken issue(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(accessTokenLifetime);
        String token = JWT.create()
                .withIssuer(issuer)
                .withSubject(String.valueOf(user.getUserId()))
                .withClaim("email", user.getEmail())
                .withClaim("role", user.getRole())
                .withClaim("type", "access")
                .withIssuedAt(now)
                .withExpiresAt(expiresAt)
                .sign(algorithm);
        return new IssuedToken(token, expiresAt);
    }

    public Long validateAndGetUserId(String token) {
        DecodedJWT jwt = verifier.verify(token);
        if (!"access".equals(jwt.getClaim("type").asString())) {
            throw new IllegalArgumentException("Invalid token type");
        }
        return Long.valueOf(jwt.getSubject());
    }

    public record IssuedToken(String value, Instant expiresAt) {}
}
