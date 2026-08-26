package com.example.Chat.DTO.Response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class MessageResponse {

    private Long messageId;
    private Long conversationId;

    private SenderResponse sender;

    private String messageType;
    private String content;

    private Long replyToMessageId;
    private Long forwardedFromMessageId;

    private boolean favorite;
    private boolean deleted;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<AttachmentResponse> attachments;
}