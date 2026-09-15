package com.example.Call.DTO.Request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateTeamRoomNotesRequest(

        @NotNull
        @Size(max = 10000)
        String notes
) {
}