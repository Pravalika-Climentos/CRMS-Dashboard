package com.example.Auth.DTO;

import java.time.Instant;

public record LoginResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        UserSessionResponse user
) {}
