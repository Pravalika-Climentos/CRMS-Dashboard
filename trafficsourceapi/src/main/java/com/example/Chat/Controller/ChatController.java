package com.example.Chat.Controller;

import com.example.Chat.DTO.Request.*;
import com.example.Chat.DTO.Response.*;
import com.example.Chat.Service.ChatService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

        private final ChatService chatService;

        // =========================================================
        // CONVERSATIONS
        // =========================================================

        @GetMapping("/conversations")
        public ResponseEntity<List<ConversationResponse>> getConversations() {

                return ResponseEntity.ok(
                                chatService.getConversations());
        }

        @PostMapping("/conversations")
        public ResponseEntity<ConversationResponse> createConversation(
                        @Valid @RequestBody CreateConversationRequest request) {

                ConversationResponse response = chatService.createConversation(request);

                return ResponseEntity
                                .status(HttpStatus.CREATED)
                                .body(response);
        }

        @PatchMapping("/conversations/{conversationId}/archive")
        public ResponseEntity<Void> archiveConversation(
                        @PathVariable Long conversationId,
                        @Valid @RequestBody ArchiveConversationRequest request) {

                chatService.archiveConversation(
                                conversationId,
                                request);

                return ResponseEntity.noContent().build();
        }

        @PatchMapping("/conversations/{conversationId}/pin")
        public ResponseEntity<Void> pinConversation(
                        @PathVariable Long conversationId,
                        @Valid @RequestBody PinConversationRequest request) {

                chatService.pinConversation(
                                conversationId,
                                request);

                return ResponseEntity.noContent().build();
        }

        @PostMapping("/conversations/{conversationId}/participants")
        public ResponseEntity<ConversationResponse> addGroupParticipants(
                        @PathVariable Long conversationId,
                        @Valid @RequestBody AddGroupParticipantsRequest request) {
                return ResponseEntity.ok(
                                chatService.addGroupParticipants(conversationId, request));
        }

        @DeleteMapping("/conversations/{conversationId}/participants/{userId}")
        public ResponseEntity<Void> removeGroupParticipant(
                        @PathVariable Long conversationId,
                        @PathVariable Long userId) {
                chatService.removeGroupParticipant(conversationId, userId);
                return ResponseEntity.noContent().build();
        }

        // =========================================================
        // MESSAGES
        // =========================================================

        @GetMapping("/conversations/{conversationId}/messages")
        public ResponseEntity<List<MessageResponse>> getMessages(
                        @PathVariable Long conversationId,

                        @RequestParam(defaultValue = "0") int page,

                        @RequestParam(defaultValue = "50") int size) {

                return ResponseEntity.ok(
                                chatService.getMessages(
                                                conversationId,
                                                page,
                                                size));
        }

        @PostMapping("/messages")
        public ResponseEntity<MessageResponse> sendMessage(
                        @Valid @RequestBody SendMessageRequest request) {

                MessageResponse response = chatService.sendMessage(request);

                return ResponseEntity
                                .status(HttpStatus.CREATED)
                                .body(response);
        }

        @PatchMapping("/messages/{messageId}")
        public ResponseEntity<MessageResponse> updateMessage(
                        @PathVariable Long messageId,
                        @Valid @RequestBody UpdateMessageRequest request) {

                return ResponseEntity.ok(
                                chatService.updateMessage(
                                                messageId,
                                                request));
        }

        @DeleteMapping("/messages/{messageId}")
        public ResponseEntity<Void> deleteMessage(
                        @PathVariable Long messageId) {

                chatService.deleteMessage(messageId);

                return ResponseEntity.noContent().build();
        }

        @PatchMapping("/messages/{messageId}/read")
        public ResponseEntity<Void> markMessageRead(
                        @PathVariable Long messageId) {

                chatService.markMessageRead(messageId);

                return ResponseEntity.noContent().build();
        }

        @PatchMapping("/messages/{messageId}/favorite")
        public ResponseEntity<Void> favoriteMessage(
                        @PathVariable Long messageId,
                        @Valid @RequestBody FavoriteMessageRequest request) {

                chatService.favoriteMessage(
                                messageId,
                                request);

                return ResponseEntity.noContent().build();
        }

        @PostMapping("/messages/{messageId}/forward")
        public ResponseEntity<MessageResponse> forwardMessage(
                        @PathVariable Long messageId,
                        @Valid @RequestBody ForwardMessageRequest request) {

                MessageResponse response = chatService.forwardMessage(
                                messageId,
                                request);

                return ResponseEntity
                                .status(HttpStatus.CREATED)
                                .body(response);
        }

        // =========================================================
        // USERS
        // =========================================================

        @GetMapping("/users")
        public ResponseEntity<List<UserSearchResponse>> searchUsers(
                        @RequestParam(name = "search", defaultValue = "") String search) {

                return ResponseEntity.ok(
                                chatService.searchUsers(search));
        }

        @GetMapping("/users/{userId}/status")
        public ResponseEntity<UserStatusResponse> getUserStatus(
                        @PathVariable Long userId) {

                return ResponseEntity.ok(
                                chatService.getUserStatus(userId));
        }

        // =========================================================
        // ATTACHMENTS
        // =========================================================

        @PostMapping(value = "/messages/{messageId}/attachments", consumes = "multipart/form-data")
        public ResponseEntity<AttachmentResponse> addAttachment(
                        @PathVariable Long messageId,

                        @RequestParam("file") MultipartFile file) {

                AttachmentResponse response = chatService.addAttachment(
                                messageId,
                                file);

                return ResponseEntity
                                .status(HttpStatus.CREATED)
                                .body(response);
        }

        @PatchMapping("/conversations/{conversationId}/unread")
        public ResponseEntity<Void> markConversationUnread(
                        @PathVariable Long conversationId) {
                chatService.markConversationUnread(conversationId);
                return ResponseEntity.noContent().build();
        }

}