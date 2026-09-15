package com.example.Call.DTO.Realtime;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.Map;

public record CallOfferRequest(

        @NotBlank
        String callId,

        @NotBlank
        @Email
        String to,

        @NotBlank
        @Pattern(
            regexp = "(?i)audio|video",
            message = "type must be audio or video."
        )
        String type,

        @NotNull
        Map<String, Object> offer
) {
}