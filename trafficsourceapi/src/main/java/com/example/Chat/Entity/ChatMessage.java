package com.example.Chat.Entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "chat_messages",
    indexes = {
        @Index(
            name = "idx_chat_messages_conversation_created",
            columnList = "conversation_id, created_at"
        ),
        @Index(
            name = "idx_chat_messages_sender",
            columnList = "sender_user_id"
        ),
        @Index(
            name = "idx_chat_messages_reply_to",
            columnList = "reply_to_message_id"
        ),
        @Index(
            name = "idx_chat_messages_forwarded_from",
            columnList = "forwarded_from_message_id"
        ),
        @Index(
            name = "idx_chat_messages_deleted_at",
            columnList = "deleted_at"
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Long messageId;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "sender_user_id", nullable = false)
    private Long senderUserId;

    @Column(name = "message_type", nullable = false, length = 30)
    private String messageType = "TEXT";

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "reply_to_message_id")
    private Long replyToMessageId;

    @Column(name = "forwarded_from_message_id")
    private Long forwardedFromMessageId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();

        if (createdAt == null) {
            createdAt = now;
        }

        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
