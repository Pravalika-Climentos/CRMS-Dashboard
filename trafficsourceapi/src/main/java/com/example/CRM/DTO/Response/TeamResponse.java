package com.example.CRM.DTO.Response;

import com.example.Common.DTO.Response.UserSummaryResponse;

import java.time.Instant;

public record TeamResponse(
        String teamId,
        String name,
        String description,
        UserSummaryResponse owner,
        boolean active,
        long memberCount,
        long version,
        Instant createdAt,
        Instant updatedAt,
        TeamPermissionsResponse permissions
) {
}
