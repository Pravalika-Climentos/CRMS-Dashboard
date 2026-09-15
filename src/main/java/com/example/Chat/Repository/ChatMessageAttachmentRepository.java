package com.example.Chat.Repository;

import com.example.Chat.Entity.ChatMessageAttachment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMessageAttachmentRepository
        extends JpaRepository<ChatMessageAttachment, Long> {

    /*
     * Returns only attachments belonging to the requested message.
     */
    List<ChatMessageAttachment> findByMessageIdOrderByAttachmentIdAsc(
            Long messageId
    );


    /*
     * Useful when returning a page of messages.
     *
     * Fetches attachments for ALL message IDs in one query,
     * avoiding one attachment query per message.
     */
    @Query("""
        SELECT a
        FROM ChatMessageAttachment a
        WHERE a.messageId IN :messageIds
        ORDER BY a.messageId, a.attachmentId
        """)
    List<ChatMessageAttachment> findByMessageIds(
            @Param("messageIds") List<Long> messageIds
    );
}