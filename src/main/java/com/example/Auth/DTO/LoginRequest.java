package com.example.Auth.DTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Email is required.")
        @Email(message = "Enter a valid email address.")
        @Size(max = 150, message = "Email cannot exceed 150 characters.")
        String email,

        @NotBlank(message = "Password is required.")
        @Size(max = 200, message = "Password cannot exceed 200 characters.")
        String password
) {}
