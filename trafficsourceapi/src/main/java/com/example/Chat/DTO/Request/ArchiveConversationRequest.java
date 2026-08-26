package com.example.Chat.DTO.Request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ArchiveConversationRequest {

    @NotNull
    private Boolean archived;
}