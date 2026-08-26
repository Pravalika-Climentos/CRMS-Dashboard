package com.example.Chat.DTO.Request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PinConversationRequest {

    @NotNull
    private Boolean pinned;
}