package com.example.Chat.Entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "chat_participants",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_chat_participants_conversation_user",
            columnNames = {"conversation_id", "user_id"}
        )
    },
    indexes = {
        @Index(
            name = "idx_chat_participants_user",
            columnList = "user_id"
        ),
        @Index(
            name = "idx_chat_participants_conversation",
            columnList = "conversation_id"
        ),
        @Index(
            name = "idx_chat_participants_last_read",
            columnList = "last_read_message_id"
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
public class ChatParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "participant_id")
    private Long participantId;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    @Column(name = "is_archived", nullable = false)
    private Boolean archived = false;

    @Column(name = "is_pinned", nullable = false)
    private Boolean pinned = false;

    @Column(name = "is_manually_unread", nullable = false)
    private Boolean manuallyUnread = false;


    @PrePersist
    protected void onCreate() {
        if (joinedAt == null) {
            joinedAt = LocalDateTime.now();
        }

        if (archived == null) {
            archived = false;
        }

        if (pinned == null) {
            pinned = false;
        }

        if (manuallyUnread == null) {
            manuallyUnread = false;
        }
    }
}