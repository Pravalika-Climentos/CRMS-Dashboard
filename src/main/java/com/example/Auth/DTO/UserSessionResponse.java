package com.example.Auth.DTO;

public record UserSessionResponse(
        Long userId,
        String fullName,
        String email,
        String role,
        String designation,
        String avatar
) {}
