package com.example.Common.DTO.Response;

public record UserSummaryResponse(
        String userId,
        String fullName,
        String designation,
        String avatar,
        Boolean active
) {
}