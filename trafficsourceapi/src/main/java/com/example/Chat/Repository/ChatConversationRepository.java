package com.example.Chat.Repository;

import com.example.Chat.Entity.ChatConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChatConversationRepository
        extends JpaRepository<ChatConversation, Long> {

    /*
     * Finds an existing DIRECT conversation containing exactly
     * the two supplied users.
     *
     * Prevents duplicate direct conversations.
     */
    @Query(value = """
        SELECT c.*
        FROM chat_conversations c
        JOIN chat_participants p
            ON p.conversation_id = c.conversation_id
        WHERE c.conversation_type = 'DIRECT'
          AND p.user_id IN (:userId1, :userId2)
        GROUP BY c.conversation_id
        HAVING COUNT(DISTINCT p.user_id) = 2
           AND (
                SELECT COUNT(*)
                FROM chat_participants cp
                WHERE cp.conversation_id = c.conversation_id
           ) = 2
        LIMIT 1
        """, nativeQuery = true)
    Optional<ChatConversation> findDirectConversationBetweenUsers(
            @Param("userId1") Long userId1,
            @Param("userId2") Long userId2
    );
}