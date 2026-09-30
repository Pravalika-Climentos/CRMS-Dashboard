package com.example.Auth.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "Password reset request is invalid.") String challengeToken,
        @NotBlank(message = "Verification code is required.")
        @Pattern(regexp = "^\\d{6}$", message = "Enter the 6-digit verification code.") String code,
        @NotBlank(message = "New password is required.")
        @Size(min = 8, max = 72, message = "New password must contain between 8 and 72 characters.")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S+$",
                message = "New password must include at least one letter, one number, and one special character, with no spaces."
        )
        String newPassword
) {}
