package com.example.Call.DTO.Response;

public record LiveKitTokenResponse(
        String token,
        String url,
        String room,
        String identity,
        String name
) {
}