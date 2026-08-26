package com.example.Chat.DTO.Request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CreateConversationRequest {

    private String conversationType;

    private String title;

    @NotEmpty(message = "At least one participant is required")
    private List<Long> participantUserIds;
}