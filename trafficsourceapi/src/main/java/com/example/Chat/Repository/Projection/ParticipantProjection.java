package com.example.Chat.Repository.Projection;

public interface ParticipantProjection {

    Long getConversationId();

    Long getUserId();

    String getFullName();

    String getAvatar();

    String getDesignation();
}