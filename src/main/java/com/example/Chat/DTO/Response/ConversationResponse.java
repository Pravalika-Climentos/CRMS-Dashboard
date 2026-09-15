package com.example.Chat.DTO.Response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ConversationResponse {

    private Long conversationId;
    private String conversationType;
    private String title;

    private boolean pinned;
    private boolean archived;

    private Long unreadCount;

    private MessagePreview lastMessage;

    private List<ParticipantResponse> participants;

    @Getter
    @Builder
    public static class MessagePreview {

        private Long messageId;
        private String content;
        private LocalDateTime createdAt;
    }
}