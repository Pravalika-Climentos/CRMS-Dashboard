package com.example.Chat.DTO.Request;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddGroupParticipantsRequest {

    @NotEmpty(message = "At least one participant is required")
    private List<Long> participantUserIds;
}                              
