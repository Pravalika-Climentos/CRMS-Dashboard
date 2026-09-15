package com.example.Call.DTO.Realtime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record CallAnswerRequest(

        @NotBlank
        String callId,

        /*
         * Kept for compatibility with communication.js.
         * Authorization will still come from the JWT.
         */
        String to,

        @NotNull
        Map<String, Object> answer
) {
}