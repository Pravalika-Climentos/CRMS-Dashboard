package com.example.Calendar.DTO.Response;

public record CalendarUserResponse(
        String userId,
        String fullName,
        String email,
        String designation,
        String role,
        String avatar
) {
}