package com.example.Auth.DTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Full name is required.")
        @Size(max = 150, message = "Full name cannot exceed 150 characters.")
        String fullName,

        @NotBlank(message = "Email is required.")
        @Email(message = "Enter a valid email address.")
        @Size(max = 150, message = "Email cannot exceed 150 characters.")
        String email,

        @NotBlank(message = "Password is required.")
        @Size(min = 8, max = 72, message = "Password must contain between 8 and 72 characters.")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S+$",
                message = "Password must include at least one letter, one number, and one special character, with no spaces."
        )
        String password
) {}
