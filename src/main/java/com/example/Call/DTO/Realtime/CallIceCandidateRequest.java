package com.example.Call.DTO.Realtime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record CallIceCandidateRequest(

        @NotBlank
        String callId,

        String to,

        @NotNull
        Map<String, Object> candidate
) {
}