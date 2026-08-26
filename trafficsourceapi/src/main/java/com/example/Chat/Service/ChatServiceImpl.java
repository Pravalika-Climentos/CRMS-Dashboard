package com.example.Chat.Service;

import com.example.Chat.DTO.Request.*;
import com.example.Chat.DTO.Response.*;

import com.example.Chat.Entity.*;

import com.example.Chat.Repository.*;
import com.example.Chat.Repository.Projection.*;
import com.example.Chat.WebSocket.ChatEventPublisher;
import com.example.Chat.WebSocket.ChatEventType;
import com.example.CRM.Entity.User;
import com.example.CRM.Repository.UserRepository;
import com.example.CRM.Repository.Projection.ChatUserProjection;

import com.example.Common.Service.CurrentUserService;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import java.time.LocalDateTime;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatServiceImpl implements ChatService {

    private static final int MAX_MESSAGE_PAGE_SIZE = 100;
    private static final int USER_SEARCH_LIMIT = 20;

    private final ChatConversationRepository conversationRepository;
    private final ChatParticipantRepository participantRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatMessageUserStateRepository messageUserStateRepository;
    private final ChatMessageAttachmentRepository attachmentRepository;
    private final ChatEventPublisher chatEventPublisher;

    private final UserRepository userRepository;

    private final CurrentUserService currentUserService;


    // =========================================================
    // CONVERSATIONS
    // =========================================================

    @Override
    public List<ConversationResponse> getConversations() {

        Long currentUserId =
                currentUserService.getCurrentUserId();

        List<ConversationSummaryProjection> summaries =
                participantRepository
                        .findConversationSummaries(currentUserId);

        if (summaries.isEmpty()) {
            return List.of();
        }

        List<Long> conversationIds =
                summaries.stream()
                        .map(
                            ConversationSummaryProjection
                                    ::getConversationId
                        )
                        .toList();

        /*
         * One batch query for ALL participants.
         *
         * No participant query per conversation.
         */
        List<ParticipantProjection> participantRows =
                participantRepository
                        .findParticipantsForConversations(
                                conversationIds
                        );

        Map<Long, List<ParticipantResponse>>
                participantsByConversation =
                participantRows.stream()
                        .collect(
                            Collectors.groupingBy(
                                ParticipantProjection
                                        ::getConversationId,

                                Collectors.mapping(
                                    this::mapParticipant,
                                    Collectors.toList()
                                )
                            )
                        );

        return summaries.stream()
                .map(summary ->
                        mapConversation(
                                summary,
                                participantsByConversation
                                        .getOrDefault(
                                            summary.getConversationId(),
                                            List.of()
                                        )
                        )
                )
                .toList();
    }


    @Override
    @Transactional
    public ConversationResponse createConversation(
            CreateConversationRequest request
    ) {

        Long currentUserId =
                currentUserService.getCurrentUserId();

        String type =
                normalizeConversationType(
                        request.getConversationType()
                );

        /*
         * Remove duplicates and current user from the supplied
         * participant list.
         */
        LinkedHashSet<Long> requestedUsers =
                new LinkedHashSet<>(
                        request.getParticipantUserIds()
                );

        requestedUsers.remove(currentUserId);

        if (requestedUsers.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one other participant is required."
            );
        }


        // -----------------------------------------------------
        // DIRECT
        // -----------------------------------------------------

        if ("DIRECT".equals(type)) {

            if (requestedUsers.size() != 1) {
                throw new IllegalArgumentException(
                        "A DIRECT conversation must contain exactly two users."
                );
            }

            Long otherUserId =
                    requestedUsers.iterator().next();

            validateActiveUser(otherUserId);

            Optional<ChatConversation> existing =
                    conversationRepository
                            .findDirectConversationBetweenUsers(
                                    currentUserId,
                                    otherUserId
                            );

            if (existing.isPresent()) {
                return buildConversationResponse(
                        existing.get().getConversationId(),
                        currentUserId
                );
            }
        }


        // -----------------------------------------------------
        // GROUP
        // -----------------------------------------------------

        else if ("GROUP".equals(type)) {

            if (request.getTitle() == null ||
                request.getTitle().isBlank()) {

                throw new IllegalArgumentException(
                        "Group conversation title is required."
                );
            }

            validateActiveUsers(requestedUsers);
        }

        else {
            throw new IllegalArgumentException(
                    "Unsupported conversation type."
            );
        }


        ChatConversation conversation =
                new ChatConversation();

        conversation.setConversationType(type);

        conversation.setTitle(
                "GROUP".equals(type)
                    ? request.getTitle().trim()
                    : null
        );

        conversation.setCreatedBy(currentUserId);

        ChatConversation savedConversation =
                conversationRepository.save(conversation);


        /*
         * Create all participant rows together.
         */
        List<ChatParticipant> participants =
                new ArrayList<>();

        participants.add(
                createParticipant(
                        savedConversation.getConversationId(),
                        currentUserId
                )
        );

        for (Long userId : requestedUsers) {

            participants.add(
                    createParticipant(
                            savedConversation.getConversationId(),
                            userId
                    )
            );
        }

        participantRepository.saveAll(participants);

        return buildConversationResponse(
                savedConversation.getConversationId(),
                currentUserId
        );
    }


    @Override
    @Transactional
    public void archiveConversation(
            Long conversationId,
            ArchiveConversationRequest request
    ) {

        Long currentUserId =
                currentUserService.getCurrentUserId();

        int updated =
                participantRepository.updateArchived(
                        conversationId,
                        currentUserId,
                        request.getArchived()
                );

        if (updated == 0) {
            throw new NoSuchElementException(
                    "Conversation not found."
            );
        }
    }

      @Override
    @Transactional
    public void markConversationUnread(Long conversationId) {
        Long currentUserId = currentUserService.getCurrentUserId();
        int updated = participantRepository.markConversationUnread(
                conversationId,
                currentUserId
        );
        if (updated == 0) {
            throw new NoSuchElementException("Conversation not found.");
        }
    }

    @Override
    @Transactional
    public void pinConversation(
            Long conversationId,
            PinConversationRequest request
    ) {

        Long currentUserId =
                currentUserService.getCurrentUserId();

        int updated =
                participantRepository.updatePinned(
                        conversationId,
                        currentUserId,
                        request.getPinned()
                );

        if (updated == 0) {
            throw new NoSuchElementException(
                    "Conversation not found."
            );
        }
    }

      @Override
    @Transactional
    public ConversationResponse addGroupParticipants(
            Long conversationId,
            AddGroupParticipantsRequest request
    ) {
        Long currentUserId = currentUserService.getCurrentUserId();

        ChatConversation conversation = conversationRepository
                .findById(conversationId)
                .orElseThrow(() ->
                        new NoSuchElementException("Conversation not found.")
                );

        validateGroupManager(conversation, currentUserId);

        LinkedHashSet<Long> requestedUserIds =
                new LinkedHashSet<>(request.getParticipantUserIds());

        requestedUserIds.remove(null);
        requestedUserIds.remove(currentUserId);

        Set<Long> existingUserIds = new HashSet<>(
                participantRepository.findUserIdsByConversationId(conversationId)
        );

        requestedUserIds.removeAll(existingUserIds);

        if (requestedUserIds.isEmpty()) {
            return buildConversationResponse(conversationId, currentUserId);
        }

        validateActiveUsers(requestedUserIds);

        List<ChatParticipant> newParticipants = requestedUserIds.stream()
                .map(userId -> createParticipant(conversationId, userId))
                .toList();

        participantRepository.saveAll(newParticipants);

        ConversationResponse response =
        buildConversationResponse(
                conversationId,
                currentUserId
        );

chatEventPublisher.publish(
        ChatEventType.PARTICIPANT_ADDED,
        conversationId,
        null,
        currentUserId,
        response
);

return response;
    }

    @Override
    @Transactional
    public void removeGroupParticipant(
            Long conversationId,
            Long userId
    ) {
        Long currentUserId = currentUserService.getCurrentUserId();

        ChatConversation conversation = conversationRepository
                .findById(conversationId)
                .orElseThrow(() ->
                        new NoSuchElementException("Conversation not found.")
                );

        validateGroupManager(conversation, currentUserId);

        if (userId == null) {
            throw new IllegalArgumentException("Participant user ID is required.");
        }

        if (Objects.equals(userId, conversation.getCreatedBy())) {
            throw new IllegalArgumentException(
                    "The group creator cannot be removed."
            );
        }

        if (!participantRepository.existsByConversationIdAndUserId(
                conversationId,
                userId
        )) {
            throw new NoSuchElementException(
                    "Participant is not a member of this group."
            );
        }

        if (participantRepository.countByConversationId(conversationId) <= 2) {
            throw new IllegalArgumentException(
                    "A group conversation must retain at least two participants."
            );
        }

        int deleted = participantRepository
                .deleteByConversationIdAndUserId(conversationId, userId);

        chatEventPublisher.publish(
        ChatEventType.PARTICIPANT_REMOVED,
        conversationId,
        null,
        currentUserId,
        userId
);

        if (deleted == 0) {
            throw new NoSuchElementException(
                    "Participant is not a member of this group."
            );
        }
    }


    private void validateGroupManager(
            ChatConversation conversation,
            Long currentUserId
    ) {
        if (!"GROUP".equalsIgnoreCase(conversation.getConversationType())) {
            throw new IllegalArgumentException(
                    "Participants can only be changed for group conversations."
            );
        }

        if (!Objects.equals(conversation.getCreatedBy(), currentUserId)) {
            throw new IllegalArgumentException(
                    "Only the group creator can manage participants."
            );
        }
    }

    // =========================================================
    // MESSAGES
    // =========================================================

    @Override
    public List<MessageResponse> getMessages(
            Long conversationId,
            int page,
            int size
    ) {

        Long currentUserId =
                currentUserService.getCurrentUserId();

        validateConversationAccess(
                conversationId,
                currentUserId
        );

        int safePage = Math.max(page, 0);

        int safeSize =
                Math.min(
                    Math.max(size, 1),
                    MAX_MESSAGE_PAGE_SIZE
                );

        Pageable pageable =
                PageRequest.of(
                        safePage,
                        safeSize
                );

        /*
         * One query:
         *
         * message
         * sender
         * current user's favorite state
         */
        List<MessageProjection> messages =
                messageRepository.findMessagePage(
                        conversationId,
                        currentUserId,
                        pageable
                );

        if (messages.isEmpty()) {
            return List.of();
        }

        List<Long> messageIds =
                messages.stream()
                        .map(MessageProjection::getMessageId)
                        .toList();

        /*
         * One additional batch query for attachments.
         */
        List<ChatMessageAttachment> attachments =
                attachmentRepository
                        .findByMessageIds(messageIds);

        Map<Long, List<AttachmentResponse>>
                attachmentsByMessage =
                attachments.stream()
                        .collect(
                            Collectors.groupingBy(
                                ChatMessageAttachment::getMessageId,

                                Collectors.mapping(
                                    this::mapAttachment,
                                    Collectors.toList()
                                )
                            )
                        );

        return messages.stream()
                .map(message ->
                        mapMessage(
                                message,
                                attachmentsByMessage
                                        .getOrDefault(
                                            message.getMessageId(),
                                            List.of()
                                        )
                        )
                )
                .toList();
    }


    @Override
    @Transactional
    public MessageResponse sendMessage(
            SendMessageRequest request
    ) {

        Long currentUserId =
                currentUserService.getCurrentUserId();

        validateConversationAccess(
                request.getConversationId(),
                currentUserId
        );

        String content =
                normalizeMessageContent(
                        request.getContent()
                );

        if (content == null) {
            throw new IllegalArgumentException(
                    "Message content is required."
            );
        }


        /*
         * If this is a reply, validate that the referenced
         * message belongs to the SAME conversation.
         */
        if (request.getReplyToMessageId() != null) {

            messageRepository
                    .findByMessageIdAndConversationId(
                            request.getReplyToMessageId(),
                            request.getConversationId()
                    )
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                "Reply message does not belong to this conversation."
                            )
                    );
        }


        ChatMessage message =
                new ChatMessage();

        message.setConversationId(
                request.getConversationId()
        );

        message.setSenderUserId(
                currentUserId
        );

        message.setMessageType("TEXT");

        message.setContent(content);

        message.setReplyToMessageId(
                request.getReplyToMessageId()
        );

        ChatMessage saved =
                messageRepository.save(message);

       MessageResponse response =
        buildMessageResponse(
                saved.getMessageId(),
                currentUserId
        );

       chatEventPublisher.publish(
        ChatEventType.MESSAGE_CREATED,
        saved.getConversationId(),
        saved.getMessageId(),
        currentUserId,
        response
      );

     return response;
 }


    @Override
    @Transactional
    public MessageResponse updateMessage(
            Long messageId,
            UpdateMessageRequest request
    ) {

        Long currentUserId =
                currentUserService.getCurrentUserId();

        String content =
                normalizeMessageContent(
                        request.getContent()
                );

        if (content == null) {
            throw new IllegalArgumentException(
                    "Message content cannot be empty."
            );
        }

        int updated =
                messageRepository.updateMessageContent(
                        messageId,
                        currentUserId,
                        content,
                        LocalDateTime.now()
                );

        if (updated == 0) {
            throw new NoSuchElementException(
                    "Message not found or cannot be edited."
            );
        }

       MessageResponse response =
        buildMessageResponse(
                messageId,
                currentUserId
        );

       chatEventPublisher.publish(
        ChatEventType.MESSAGE_UPDATED,
        response.getConversationId(),
        messageId,
        currentUserId,
        response
       );

       return response;
    }


    @Override
    @Transactional
    public void deleteMessage(Long messageId) {

        Long currentUserId =
                currentUserService.getCurrentUserId();

        ChatMessage message =
        messageRepository.findById(messageId)
                .orElseThrow(() ->
                        new NoSuchElementException(
                                "Message not found."
                        )
                );

        Long conversationId =
        message.getConversationId();

        int updated =
                messageRepository.softDeleteMessage(
                        messageId,
                        currentUserId,
                        LocalDateTime.now()
                );

        chatEventPublisher.publish(
                 ChatEventType.MESSAGE_DELETED,
                 conversationId,
                 messageId,
                 currentUserId,
         null
         );

        if (updated == 0) {
            throw new NoSuchElementException(
                    "Message not found or cannot be deleted."
            );
        }
    }


    @Override
    @Transactional
    public void markMessageRead(Long messageId) {

        Long currentUserId =
                currentUserService.getCurrentUserId();

        ChatMessage message =
                messageRepository.findById(messageId)
                        .orElseThrow(() ->
                                new NoSuchElementException(
                                    "Message not found."
                                )
                        );

        Long conversationId =
                message.getConversationId();

        validateConversationAccess(
                conversationId,
                currentUserId
        );

        /*
         * Important:
         * Read position should move FORWARD only.
         *
         * We'll improve the repository query below.
         */
      participantRepository
        .advanceLastReadMessage(
                conversationId,
                currentUserId,
                messageId
        );
    }


    @Override
    @Transactional
    public void favoriteMessage(
            Long messageId,
            FavoriteMessageRequest request
    ) {

        Long currentUserId =
                currentUserService.getCurrentUserId();

        ChatMessage message =
                messageRepository.findById(messageId)
                        .orElseThrow(() ->
                                new NoSuchElementException(
                                    "Message not found."
                                )
                        );

        validateConversationAccess(
                message.getConversationId(),
                currentUserId
        );


        if (Boolean.TRUE.equals(request.getFavorite())) {

            ChatMessageUserStateId id =
                    new ChatMessageUserStateId(
                            messageId,
                            currentUserId
                    );

            /*
             * Don't SELECT before save.
             *
             * save() will persist/merge using the composite ID.
             */
            ChatMessageUserState state =
                    new ChatMessageUserState();

            state.setId(id);
            state.setFavorite(true);

            messageUserStateRepository.save(state);

        } else {

            /*
             * Sparse table:
             * no row = not favorite.
             */
            messageUserStateRepository
                    .deleteFavoriteState(
                            messageId,
                            currentUserId
                    );
        }
    }


    @Override
    @Transactional
    public MessageResponse forwardMessage(
            Long messageId,
            ForwardMessageRequest request
    ) {

        Long currentUserId =
                currentUserService.getCurrentUserId();

        ChatMessage original =
                messageRepository.findById(messageId)
                        .orElseThrow(() ->
                                new NoSuchElementException(
                                    "Original message not found."
                                )
                        );

        validateConversationAccess(
                original.getConversationId(),
                currentUserId
        );

        validateConversationAccess(
                request.getTargetConversationId(),
                currentUserId
        );

        if (original.getDeletedAt() != null) {
            throw new IllegalArgumentException(
                    "Deleted messages cannot be forwarded."
            );
        }

        ChatMessage forwarded =
                new ChatMessage();

        forwarded.setConversationId(
                request.getTargetConversationId()
        );

        forwarded.setSenderUserId(
                currentUserId
        );

        forwarded.setMessageType(
                original.getMessageType()
        );

        forwarded.setContent(
                original.getContent()
        );

        /*
         * Point to the actual source message.
     
        *
         * If someone forwards an already-forwarded message,
         * preserve the original source where possible.
         */
        forwarded.setForwardedFromMessageId(
                original.getForwardedFromMessageId() != null
                    ? original.getForwardedFromMessageId()
                    : original.getMessageId()
        );

        ChatMessage saved =
                messageRepository.save(forwarded);

        List<ChatMessageAttachment> originalAttachments =
            attachmentRepository
                    .findByMessageIdOrderByAttachmentIdAsc(
                            original.getMessageId()
                    );

    if (!originalAttachments.isEmpty()) {
        List<ChatMessageAttachment> forwardedAttachments =
                originalAttachments.stream()
                        .map(source -> {
                            ChatMessageAttachment copy =
                                    new ChatMessageAttachment();

                            copy.setMessageId(saved.getMessageId());
                            copy.setFileName(source.getFileName());
                            copy.setFilePath(source.getFilePath());
                            copy.setMimeType(source.getMimeType());
                            copy.setFileSize(source.getFileSize());

                            return copy;
                        })
                        .toList();

        attachmentRepository.saveAll(forwardedAttachments);
    }

        MessageResponse response =
        buildMessageResponse(
                saved.getMessageId(),
                currentUserId
        );

       chatEventPublisher.publish(
        ChatEventType.MESSAGE_CREATED,
        saved.getConversationId(),
        saved.getMessageId(),
        currentUserId,
        response
        );

        return response;
    }


    // =========================================================
    // USERS
    // =========================================================

    @Override
    public List<UserSearchResponse> searchUsers(
            String search
    ) {

        Long currentUserId =
                currentUserService.getCurrentUserId();

        String safeSearch =
                search == null
                    ? ""
                    : search.trim();

        Pageable pageable =
                PageRequest.of(
                        0,
                        USER_SEARCH_LIMIT
                );

        List<ChatUserProjection> users =
                userRepository.searchChatUsers(
                        safeSearch,
                        currentUserId,
                        pageable
                );

        return users.stream()
                .map(user ->
                        UserSearchResponse.builder()
                                .userId(user.getUserId())
                                .fullName(user.getFullName())
                                .email(user.getEmail())
                                .designation(user.getDesignation())
                                .role(user.getRole())
                                .avatar(user.getAvatar())
                                .build()
                )
                .toList();
    }


    @Override
    public UserStatusResponse getUserStatus(
            Long userId
    ) {

        User user =
                userRepository.findById(userId)
                        .orElseThrow(() ->
                                new NoSuchElementException(
                                    "User not found."
                                )
                        );

        return UserStatusResponse.builder()
                .userId(user.getUserId())
                .active(
                    Boolean.TRUE.equals(
                        user.getActive()
                    )
                )
                .build();
    }


    // =========================================================
    // ATTACHMENTS
    // =========================================================

    @Override
    @Transactional
    public AttachmentResponse addAttachment(
            Long messageId,
            MultipartFile file
    ) {

        Long currentUserId =
                currentUserService.getCurrentUserId();

        ChatMessage message =
                messageRepository.findById(messageId)
                        .orElseThrow(() ->
                                new NoSuchElementException(
                                    "Message not found."
                                )
                        );

        validateConversationAccess(
                message.getConversationId(),
                currentUserId
        );

        /*
         * Only sender can attach a file to an existing message.
         */
        if (!Objects.equals(
                message.getSenderUserId(),
                currentUserId
        )) {
            throw new IllegalArgumentException(
                    "You cannot add an attachment to another user's message."
            );
        }

        if (message.getDeletedAt() != null) {
            throw new IllegalArgumentException(
                    "Cannot attach files to a deleted message."
            );
        }

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "Attachment file is required."
            );
        }


        String originalFileName =
                file.getOriginalFilename();

        if (originalFileName == null ||
            originalFileName.isBlank()) {

            originalFileName = "attachment";
        }

        /*
         * Never use the client filename directly as the stored
         * physical filename.
         */
        String safeOriginalName =
                Paths.get(originalFileName)
                        .getFileName()
                        .toString();

        String storedFileName =
                UUID.randomUUID()
                        + "_"
                        + safeOriginalName;

        Path uploadDirectory =
                Paths.get(
                    "uploads",
                    "chat",
                    String.valueOf(messageId)
                );

        try {

            Files.createDirectories(uploadDirectory);

            Path destination =
                    uploadDirectory.resolve(
                            storedFileName
                    );

            Files.copy(
                    file.getInputStream(),
                    destination,
                    StandardCopyOption.REPLACE_EXISTING
            );

            ChatMessageAttachment attachment =
                    new ChatMessageAttachment();

            attachment.setMessageId(messageId);
            attachment.setFileName(safeOriginalName);

            attachment.setFilePath(
                   "/" + destination
                            .toString()
                            .replace("\\", "/")
            );

            attachment.setMimeType(
                    file.getContentType()
            );

            attachment.setFileSize(
                    file.getSize()
            );

            ChatMessageAttachment saved =
                    attachmentRepository.save(
                            attachment
                    );
            
            MessageResponse updatedMessage =
        buildMessageResponse(
                messageId,
                currentUserId
        );

        chatEventPublisher.publish(
        ChatEventType.ATTACHMENT_ADDED,
        message.getConversationId(),
        messageId,
        currentUserId,
        updatedMessage
        );

        return mapAttachment(saved);


        } catch (IOException ex) {

            throw new IllegalStateException(
                    "Unable to store attachment.",
                    ex
            );
        }
    }


    // =========================================================
    // VALIDATION
    // =========================================================

    private void validateConversationAccess(
            Long conversationId,
            Long userId
    ) {

        boolean participant =
                participantRepository
                        .existsByConversationIdAndUserId(
                                conversationId,
                                userId
                        );

        if (!participant) {
            throw new NoSuchElementException(
                    "Conversation not found."
            );
        }
    }


    private void validateActiveUser(Long userId) {

        User user =
                userRepository.findById(userId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                    "User not found: " + userId
                                )
                        );

        if (!Boolean.TRUE.equals(user.getActive())) {

            throw new IllegalArgumentException(
                    "Inactive user cannot be added to conversation."
            );
        }
    }


   private void validateActiveUsers(
        Collection<Long> userIds
) {

    long validCount =
            userRepository
                    .countActiveUsersByIds(userIds);

    if (validCount != userIds.size()) {

        throw new IllegalArgumentException(
                "One or more users do not exist or are inactive."
        );
    }
}

    // =========================================================
    // MAPPING
    // =========================================================

    private ParticipantResponse mapParticipant(
            ParticipantProjection participant
    ) {

        return ParticipantResponse.builder()
                .userId(participant.getUserId())
                .fullName(participant.getFullName())
                .avatar(participant.getAvatar())
                .designation(participant.getDesignation())
                .build();
    }


    private ConversationResponse mapConversation(
            ConversationSummaryProjection summary,
            List<ParticipantResponse> participants
    ) {

        ConversationResponse.MessagePreview lastMessage = null;

        if (summary.getLastMessageId() != null) {

            lastMessage =
                    ConversationResponse.MessagePreview
                            .builder()
                            .messageId(
                                summary.getLastMessageId()
                            )
                            .content(
                                summary.getLastMessageContent()
                            )
                            .createdAt(
                                summary.getLastMessageAt()
                            )
                            .build();
        }

        return ConversationResponse.builder()
                .conversationId(
                    summary.getConversationId()
                )
                .conversationType(
                    summary.getConversationType()
                )
                .title(summary.getTitle())
                .pinned(
                    Boolean.TRUE.equals(
                        summary.getPinned()
                    )
                )
                .archived(
                    Boolean.TRUE.equals(
                        summary.getArchived()
                    )
                )
                .unreadCount(
                    summary.getUnreadCount() == null
                        ? 0L
                        : summary.getUnreadCount()
                )
                .lastMessage(lastMessage)
                .participants(participants)
                .build();
    }


    private MessageResponse mapMessage(
            MessageProjection message,
            List<AttachmentResponse> attachments
    ) {

        boolean deleted =
                message.getDeletedAt() != null;

        SenderResponse sender =
                SenderResponse.builder()
                        .userId(
                            message.getSenderUserId()
                        )
                        .fullName(
                            message.getSenderName()
                        )
                        .avatar(
                            message.getSenderAvatar()
                        )
                        .build();

        return MessageResponse.builder()
                .messageId(
                    message.getMessageId()
                )
                .conversationId(
                    message.getConversationId()
                )
                .sender(sender)
                .messageType(
                    message.getMessageType()
                )
                .content(
                    deleted
                        ? null
                        : message.getContent()
                )
                .replyToMessageId(
                    message.getReplyToMessageId()
                )
                .forwardedFromMessageId(
                    message.getForwardedFromMessageId()
                )
                .favorite(
                    message.getFavorite() != null
                    && message.getFavorite() == 1
                )
                .deleted(deleted)
                .createdAt(
                    message.getCreatedAt()
                )
                .updatedAt(
                    message.getUpdatedAt()
                )
                .attachments(attachments)
                .build();
    }


    private AttachmentResponse mapAttachment(
            ChatMessageAttachment attachment
    ) {

        return AttachmentResponse.builder()
                .attachmentId(
                    attachment.getAttachmentId()
                )
                .fileName(
                    attachment.getFileName()
                )
                .filePath(
                    attachment.getFilePath()
                )
                .mimeType(
                    attachment.getMimeType()
                )
                .fileSize(
                    attachment.getFileSize()
                )
                .build();
    }


    private ChatParticipant createParticipant(
            Long conversationId,
            Long userId
    ) {

        ChatParticipant participant =
                new ChatParticipant();

        participant.setConversationId(
                conversationId
        );

        participant.setUserId(userId);

        participant.setArchived(false);
        participant.setPinned(false);

        return participant;
    }


    private String normalizeConversationType(
            String type
    ) {

        if (type == null || type.isBlank()) {
            return "DIRECT";
        }

        return type
                .trim()
                .toUpperCase(Locale.ROOT);
    }


    private String normalizeMessageContent(
            String content
    ) {

        if (content == null) {
            return null;
        }

        String normalized =
                content.trim();

        return normalized.isEmpty()
                ? null
                : normalized;
    }


    // =========================================================
    // SINGLE-RESPONSE HELPERS
    // =========================================================

    private ConversationResponse buildConversationResponse(
            Long conversationId,
            Long currentUserId
    ) {

        ConversationSummaryProjection summary =
                participantRepository
                        .findConversationSummary(
                                conversationId,
                                currentUserId
                        )
                        .orElseThrow(() ->
                                new NoSuchElementException(
                                    "Conversation not found."
                                )
                        );

        List<ParticipantResponse> participants =
                participantRepository
                        .findParticipantsForConversations(
                                List.of(conversationId)
                        )
                        .stream()
                        .map(this::mapParticipant)
                        .toList();

        return mapConversation(
                summary,
                participants
        );
    }


    private MessageResponse buildMessageResponse(
            Long messageId,
            Long currentUserId
    ) {

        MessageProjection message =
                messageRepository
                        .findMessageById(
                                messageId,
                                currentUserId
                        )
                        .orElseThrow(() ->
                                new NoSuchElementException(
                                    "Message not found."
                                )
                        );

        List<AttachmentResponse> attachments =
                attachmentRepository
                        .findByMessageIdOrderByAttachmentIdAsc(
                                messageId
                        )
                        .stream()
                        .map(this::mapAttachment)
                        .toList();

        return mapMessage(
                message,
                attachments
        );
    }
}