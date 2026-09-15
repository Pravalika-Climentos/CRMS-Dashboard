package com.example.Call.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateCallRoomRequest(

        @NotBlank
        @Size(max = 150)
        String roomName,

        /*
         * Null means an ad-hoc room.
         */
        @Positive
        Long teamId
) {
}
