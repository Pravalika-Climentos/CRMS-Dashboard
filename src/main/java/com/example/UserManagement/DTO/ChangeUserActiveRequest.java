package com.example.UserManagement.DTO;

import jakarta.validation.constraints.NotNull;

public record ChangeUserActiveRequest(
        @NotNull(message = "Active status is required.")
        Boolean active
) {}