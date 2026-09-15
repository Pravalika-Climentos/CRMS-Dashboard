package com.example.Call.DTO.Realtime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CallControlRequest(

        @NotBlank
        String callId,

        /*
         * Retained for compatibility with communication.js.
         * The service will determine the real other participant
         * from call_sessions.
         */
        String to,

        @Pattern(
            regexp = "(?i)audio|video",
            message = "type must be audio or video."
        )
        String type
) {
}