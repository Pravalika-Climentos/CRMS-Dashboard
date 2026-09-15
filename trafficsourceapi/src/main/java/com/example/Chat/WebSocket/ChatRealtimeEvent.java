package com.example.Chat.WebSocket;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ChatRealtimeEvent {

    private ChatEventType eventType;

    private Long conversationId;
    private Long messageId;
    private Long actorUserId;

    private Object payload;

    private LocalDateTime occurredAt;
}