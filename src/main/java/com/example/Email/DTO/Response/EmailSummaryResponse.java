package com.example.Email.DTO.Response;
import com.example.Email.Entity.*;
import java.time.Instant;
public record EmailSummaryResponse(Long messageId, String threadKey, EmailUserResponse sender, String subject,
    String preview, EmailMessageStatus status, EmailMailboxRole mailboxRole, Instant sentAt,
    boolean read, boolean starred, boolean important, boolean archived, boolean spam,
    Instant trashedAt, long attachmentCount, Long messageVersion, Long mailboxVersion) {}
