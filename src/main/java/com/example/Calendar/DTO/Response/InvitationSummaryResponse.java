package com.example.Calendar.DTO.Response;

public record InvitationSummaryResponse(
        long pending,
        long accepted,
        long declined,
        long expired
) {
}