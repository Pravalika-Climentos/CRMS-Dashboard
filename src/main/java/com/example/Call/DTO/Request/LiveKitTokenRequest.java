package com.example.Call.DTO.Request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record LiveKitTokenRequest(

        @Size(max = 150)
        String room,

        @Positive
        Long teamId
) {
}