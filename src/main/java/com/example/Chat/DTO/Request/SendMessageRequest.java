package com.example.Chat.DTO.Request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SendMessageRequest {

    @NotNull
    private Long conversationId;

    @Size(max = 10000)
    private String content;

    private Long replyToMessageId;
}