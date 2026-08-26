package com.example.Chat.Repository.Projection;

import java.time.LocalDateTime;

public interface ConversationSummaryProjection {

    Long getConversationId();

    String getConversationType();

    String getTitle();

    Boolean getPinned();

    Boolean getArchived();

    Long getLastReadMessageId();

    Long getLastMessageId();

    String getLastMessageContent();

    LocalDateTime getLastMessageAt();

    Long getUnreadCount();
}