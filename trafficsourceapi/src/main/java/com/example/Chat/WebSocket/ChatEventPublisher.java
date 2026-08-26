package com.example.Chat.WebSocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class ChatEventPublisher {

    private final ApplicationEventPublisher publisher;

    public void publish(
            ChatEventType eventType,
            Long conversationId,
            Long messageId,
            Long actorUserId,
            Object payload
    ) {
        publisher.publishEvent(
                ChatRealtimeEvent.builder()
                        .eventType(eventType)
                        .conversationId(conversationId)
                        .messageId(messageId)
                        .actorUserId(actorUserId)
                        .payload(payload)
                        .occurredAt(LocalDateTime.now())
                        .build()
        );
    }
}