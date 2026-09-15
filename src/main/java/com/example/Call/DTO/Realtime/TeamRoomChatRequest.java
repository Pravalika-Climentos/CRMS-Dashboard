package com.example.Call.DTO.Realtime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TeamRoomChatRequest(

        @NotBlank
        @Size(max = 150)
        String room,

        @NotBlank
        @Size(max = 1000)
        String text
) {
}