package com.example.CRM.DTO.Response;

import com.example.Common.DTO.Response.UserSummaryResponse;

import java.time.Instant;

public record TeamMemberResponse(
        UserSummaryResponse user,
        String addedByUserId,
        Instant joinedAt
) {
}
