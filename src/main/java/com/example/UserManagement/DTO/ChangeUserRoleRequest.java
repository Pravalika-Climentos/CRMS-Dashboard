package com.example.UserManagement.DTO;

import com.example.CRM.Entity.UserRole;
import jakarta.validation.constraints.NotNull;

public record ChangeUserRoleRequest(
        @NotNull(message = "Role is required.")
        UserRole role
) {}