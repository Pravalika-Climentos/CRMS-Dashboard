package com.example.Chat.Repository;

import com.example.Chat.Entity.ChatMessageUserState;
import com.example.Chat.Entity.ChatMessageUserStateId;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageUserStateRepository
        extends JpaRepository<
            ChatMessageUserState,
            ChatMessageUserStateId
        > {

    /*
     * Reads only the favorite flag.
     * Does not load the entire entity.
     */
    @Query("""
        SELECT s.favorite
        FROM ChatMessageUserState s
        WHERE s.id.messageId = :messageId
          AND s.id.userId = :userId
        """)
    Boolean findFavoriteState(
            @Param("messageId") Long messageId,
            @Param("userId") Long userId
    );


    /*
     * Deletes state when favorite is removed.
     *
     * This keeps the table sparse:
     *
     * row exists = favorite
     * no row     = not favorite
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        DELETE FROM ChatMessageUserState s
        WHERE s.id.messageId = :messageId
          AND s.id.userId = :userId
        """)
    int deleteFavoriteState(
            @Param("messageId") Long messageId,
            @Param("userId") Long userId
    );
}