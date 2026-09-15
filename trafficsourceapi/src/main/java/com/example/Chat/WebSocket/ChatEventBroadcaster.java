package com.example.Chat.WebSocket;

import lombok.RequiredArgsConstructor;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ChatEventBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void broadcast(ChatRealtimeEvent event) {

        String destination =
                "/topic/chat/conversations/"
                + event.getConversationId();

        messagingTemplate.convertAndSend(
                destination,
                event
        );
    }
}