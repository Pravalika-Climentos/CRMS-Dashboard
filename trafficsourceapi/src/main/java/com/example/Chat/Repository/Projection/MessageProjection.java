package com.example.Chat.Repository.Projection;

import java.time.LocalDateTime;

public interface MessageProjection {

    Long getMessageId();

    Long getConversationId();

    Long getSenderUserId();

    String getSenderName();

    String getSenderAvatar();

    String getMessageType();

    String getContent();

    Long getReplyToMessageId();

    Long getForwardedFromMessageId();

    Integer getFavorite();

    LocalDateTime getCreatedAt();

    LocalDateTime getUpdatedAt();

    LocalDateTime getDeletedAt();
}