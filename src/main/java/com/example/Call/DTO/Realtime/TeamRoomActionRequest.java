package com.example.Call.DTO.Realtime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TeamRoomActionRequest(

        @NotBlank
        @Size(max = 150)
        String room
) {
}