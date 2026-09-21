package com.example.Email.DTO.Response;
import com.example.Email.Entity.*;
import java.time.*;
import java.util.List;
public record EmailDetailResponse(Long messageId, String threadKey, Long parentMessageId, EmailUserResponse sender,
    List<EmailRecipientResponse> recipients, String subject, String body, EmailMessageStatus status,
    EmailMailboxRole mailboxRole, Instant sentAt, boolean read, boolean starred, boolean important,
    boolean archived, boolean spam, Instant trashedAt, List<EmailAttachmentResponse> attachments,
    Long messageVersion, Long mailboxVersion, LocalDateTime createdAt, LocalDateTime updatedAt) {}
