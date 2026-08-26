package com.example.Chat.Repository;

import com.example.Chat.Entity.ChatMessage;
import com.example.Chat.Repository.Projection.ConversationSummaryProjection;
import com.example.Chat.Repository.Projection.MessageProjection;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository
        extends JpaRepository<ChatMessage, Long> {

    /*
     * Retrieves messages in a conversation using pagination.
     *
     * Deleted messages remain available because we use soft delete.
     */
    @Query("""
        SELECT m
        FROM ChatMessage m
        WHERE m.conversationId = :conversationId
        ORDER BY m.createdAt DESC, m.messageId DESC
        """)
    List<ChatMessage> findConversationMessages(
            @Param("conversationId") Long conversationId,
            Pageable pageable
    );


    /*
     * Finds a message only when it belongs to the supplied conversation.
     *
     * Useful for read/reply validation.
     */
    Optional<ChatMessage> findByMessageIdAndConversationId(
            Long messageId,
            Long conversationId
    );


    /*
     * Ownership check without fetching the entire message.
     */
    boolean existsByMessageIdAndSenderUserId(
            Long messageId,
            Long senderUserId
    );


    /*
     * Edit message directly.
     *
     * Also enforces ownership and prevents editing deleted messages.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE ChatMessage m
        SET m.content = :content,
            m.updatedAt = :updatedAt
        WHERE m.messageId = :messageId
          AND m.senderUserId = :userId
          AND m.deletedAt IS NULL
        """)
    int updateMessageContent(
            @Param("messageId") Long messageId,
            @Param("userId") Long userId,
            @Param("content") String content,
            @Param("updatedAt") LocalDateTime updatedAt
    );


    /*
     * Soft delete directly in SQL.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE ChatMessage m
        SET m.deletedAt = :deletedAt,
            m.updatedAt = :deletedAt
        WHERE m.messageId = :messageId
          AND m.senderUserId = :userId
          AND m.deletedAt IS NULL
        """)
    int softDeleteMessage(
            @Param("messageId") Long messageId,
            @Param("userId") Long userId,
            @Param("deletedAt") LocalDateTime deletedAt
    );


    /*
     * Efficient unread count.
     *
     * Database performs COUNT rather than fetching unread messages
     * and counting them in Java.
     */
    @Query("""
        SELECT COUNT(m)
        FROM ChatMessage m
        WHERE m.conversationId = :conversationId
          AND m.senderUserId <> :userId
          AND m.deletedAt IS NULL
          AND (
              :lastReadMessageId IS NULL
              OR m.messageId > :lastReadMessageId
          )
        """)
    long countUnreadMessages(
            @Param("conversationId") Long conversationId,
            @Param("userId") Long userId,
            @Param("lastReadMessageId") Long lastReadMessageId
    );

    @Query(value = """
    SELECT
        c.conversation_id AS conversationId,
        c.conversation_type AS conversationType,
        c.title AS title,

        cp.is_pinned AS pinned,
        cp.is_archived AS archived,
        cp.last_read_message_id AS lastReadMessageId,

        lm.message_id AS lastMessageId,
        lm.content AS lastMessageContent,
        lm.created_at AS lastMessageAt,

        (
            SELECT COUNT(*)
            FROM chat_messages unread
            WHERE unread.conversation_id = c.conversation_id
              AND unread.sender_user_id <> :userId
              AND unread.deleted_at IS NULL
              AND (
                    cp.last_read_message_id IS NULL
                    OR unread.message_id > cp.last_read_message_id
              )
        ) AS unreadCount

    FROM chat_participants cp

    JOIN chat_conversations c
        ON c.conversation_id = cp.conversation_id

    LEFT JOIN chat_messages lm
        ON lm.message_id = (
            SELECT m.message_id
            FROM chat_messages m
            WHERE m.conversation_id = c.conversation_id
              AND m.deleted_at IS NULL
            ORDER BY m.created_at DESC, m.message_id DESC
            LIMIT 1
        )

    WHERE cp.user_id = :userId

    ORDER BY
        cp.is_pinned DESC,
        COALESCE(lm.created_at, c.created_at) DESC
    """, nativeQuery = true)
    List<ConversationSummaryProjection> findConversationSummaries(
        @Param("userId") Long userId
);

@Query(value = """
    SELECT
        m.message_id AS messageId,
        m.conversation_id AS conversationId,

        m.sender_user_id AS senderUserId,
        u.full_name AS senderName,
        u.avatar AS senderAvatar,

        m.message_type AS messageType,

        CASE
            WHEN m.deleted_at IS NULL THEN m.content
            ELSE NULL
        END AS content,

        m.reply_to_message_id AS replyToMessageId,
        m.forwarded_from_message_id AS forwardedFromMessageId,

        COALESCE(s.is_favorite, 0) AS favorite,

        m.created_at AS createdAt,
        m.updated_at AS updatedAt,
        m.deleted_at AS deletedAt

    FROM chat_messages m

    JOIN users u
        ON u.user_id = m.sender_user_id

    LEFT JOIN chat_message_user_state s
        ON s.message_id = m.message_id
       AND s.user_id = :userId
       AND s.is_favorite = 1

    WHERE m.conversation_id = :conversationId

    ORDER BY
        m.created_at DESC,
        m.message_id DESC

    """, nativeQuery = true)
List<MessageProjection> findMessagePage(
        @Param("conversationId") Long conversationId,
        @Param("userId") Long userId,
        Pageable pageable
);

@Query(value = """
    SELECT
        m.message_id AS messageId,
        m.conversation_id AS conversationId,

        m.sender_user_id AS senderUserId,
        u.full_name AS senderName,
        u.avatar AS senderAvatar,

        m.message_type AS messageType,

        CASE
            WHEN m.deleted_at IS NULL THEN m.content
            ELSE NULL
        END AS content,

        m.reply_to_message_id AS replyToMessageId,
        m.forwarded_from_message_id AS forwardedFromMessageId,

        COALESCE(s.is_favorite, 0) AS favorite,

        m.created_at AS createdAt,
        m.updated_at AS updatedAt,
        m.deleted_at AS deletedAt

    FROM chat_messages m

    JOIN users u
        ON u.user_id = m.sender_user_id

    LEFT JOIN chat_message_user_state s
        ON s.message_id = m.message_id
       AND s.user_id = :userId
       AND s.is_favorite = 1

    WHERE m.message_id = :messageId
    """, nativeQuery = true)
Optional<MessageProjection> findMessageById(
        @Param("messageId") Long messageId,
        @Param("userId") Long userId
);
}