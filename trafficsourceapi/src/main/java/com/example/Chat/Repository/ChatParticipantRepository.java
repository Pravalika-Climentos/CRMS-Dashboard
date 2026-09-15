package com.example.Chat.Repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.Chat.Entity.ChatParticipant;
import com.example.Chat.Repository.Projection.ConversationSummaryProjection;
import com.example.Chat.Repository.Projection.ParticipantProjection;

public interface ChatParticipantRepository
        extends JpaRepository<ChatParticipant, Long> {

    /*
     * Checks whether a user belongs to a conversation.
     *
     * Used for authorization/validation before reading,
     * sending, editing or forwarding messages.
     */
    boolean existsByConversationIdAndUserId(
            Long conversationId,
            Long userId);

    /*
     * Gets the current user's participant record only when
     * we actually need participant state.
     */
    Optional<ChatParticipant> findByConversationIdAndUserId(
            Long conversationId,
            Long userId);

    long countByConversationId(Long conversationId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    int deleteByConversationIdAndUserId(
            Long conversationId,
            Long userId);

    /*
     * Returns participant user IDs only.
     *
     * Does not fetch full ChatParticipant entities.
     */
    @Query("""
            SELECT p.userId
            FROM ChatParticipant p
            WHERE p.conversationId = :conversationId
            """)
    List<Long> findUserIdsByConversationId(
            @Param("conversationId") Long conversationId);

    /*
     * Pin/unpin without first SELECTing the participant entity.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ChatParticipant p
            SET p.pinned = :pinned
            WHERE p.conversationId = :conversationId
              AND p.userId = :userId
            """)
    int updatePinned(
            @Param("conversationId") Long conversationId,
            @Param("userId") Long userId,
            @Param("pinned") boolean pinned);

    /*
     * Archive/unarchive without loading the participant entity.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ChatParticipant p
            SET p.archived = :archived
            WHERE p.conversationId = :conversationId
              AND p.userId = :userId
            """)
    int updateArchived(
            @Param("conversationId") Long conversationId,
            @Param("userId") Long userId,
            @Param("archived") boolean archived);

    /*
     * Updates read position directly.
     *
     * Avoids SELECT participant → modify → save.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ChatParticipant p
            SET p.lastReadMessageId = CASE
                    WHEN p.lastReadMessageId IS NULL
                      OR p.lastReadMessageId < :messageId
                    THEN :messageId
                    ELSE p.lastReadMessageId
                END,
                p.manuallyUnread = false
            WHERE p.conversationId = :conversationId
              AND p.userId = :userId
            """)
    int advanceLastReadMessage(
            @Param("conversationId") Long conversationId,
            @Param("userId") Long userId,
            @Param("messageId") Long messageId);

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

               GREATEST(
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
                    ),
                    CASE WHEN cp.is_manually_unread = 1 THEN 1 ELSE 0 END
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
            @Param("userId") Long userId);

    @Query(value = """
            SELECT
                cp.conversation_id AS conversationId,
                u.user_id AS userId,
                u.full_name AS fullName,
                u.avatar AS avatar,
                u.designation AS designation

            FROM chat_participants cp

            JOIN users u
                ON u.user_id = cp.user_id

            WHERE cp.conversation_id IN (:conversationIds)

            ORDER BY
                cp.conversation_id,
                cp.participant_id
            """, nativeQuery = true)
    List<ParticipantProjection> findParticipantsForConversations(
            @Param("conversationIds") List<Long> conversationIds);

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

               GREATEST(
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
                    ),
                    CASE WHEN cp.is_manually_unread = 1 THEN 1 ELSE 0 END
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
              AND cp.conversation_id = :conversationId
            """, nativeQuery = true)
    Optional<ConversationSummaryProjection> findConversationSummary(
            @Param("conversationId") Long conversationId,
            @Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ChatParticipant p
            SET p.manuallyUnread = true
            WHERE p.conversationId = :conversationId
              AND p.userId = :userId
            """)
    int markConversationUnread(
            @Param("conversationId") Long conversationId,
            @Param("userId") Long userId);
}