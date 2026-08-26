package com.example.Chat.Service;

import com.example.Chat.DTO.Request.*;
import com.example.Chat.DTO.Response.*;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ChatService {

        // Conversations
        List<ConversationResponse> getConversations();

        ConversationResponse createConversation(
                        CreateConversationRequest request);

        void archiveConversation(
                        Long conversationId,
                        ArchiveConversationRequest request);

        void pinConversation(
                        Long conversationId,
                        PinConversationRequest request);

        ConversationResponse addGroupParticipants(
                        Long conversationId,
                        AddGroupParticipantsRequest request);

        void removeGroupParticipant(
                        Long conversationId,
                        Long userId);

        // Messages
        List<MessageResponse> getMessages(
                        Long conversationId,
                        int page,
                        int size);

        MessageResponse sendMessage(
                        SendMessageRequest request);

        MessageResponse updateMessage(
                        Long messageId,
                        UpdateMessageRequest request);

        void deleteMessage(Long messageId);

        void markMessageRead(Long messageId);

        void favoriteMessage(
                        Long messageId,
                        FavoriteMessageRequest request);

        MessageResponse forwardMessage(
                        Long messageId,
                        ForwardMessageRequest request);

        // Users
        List<UserSearchResponse> searchUsers(String search);

        UserStatusResponse getUserStatus(Long userId);

        // Attachments
        AttachmentResponse addAttachment(
                        Long messageId,
                        MultipartFile file);

        void markConversationUnread(Long conversationId);
}