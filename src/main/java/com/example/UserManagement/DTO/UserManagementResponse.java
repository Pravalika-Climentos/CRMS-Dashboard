package com.example.UserManagement.DTO;

import com.example.CRM.Entity.UserRole;

public record UserManagementResponse(
        Long userId,
        String fullName,
        String email,
        String phone,
        String designation,
        UserRole role,
        Long managerUserId,
        String managerName,
        boolean active,
        boolean accountLocked
) {}