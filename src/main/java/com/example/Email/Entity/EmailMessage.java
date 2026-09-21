package com.example.Email.Entity;

import com.example.CRM.Entity.User;
import com.example.Common.Entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "email_messages", indexes = {
    @Index(name = "idx_email_messages_sender_status", columnList = "sender_user_id,status"),
    @Index(name = "idx_email_messages_thread_sent", columnList = "thread_key,sent_at"),
    @Index(name = "idx_email_messages_sent_at", columnList = "sent_at")
})
@Getter @Setter @NoArgsConstructor
public class EmailMessage extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Long messageId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_user_id", nullable = false)
    private User sender;

    @Column(name = "thread_key", nullable = false, length = 36)
    private String threadKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_message_id")
    private EmailMessage parentMessage;

    @Column(name = "subject", nullable = false, length = 255)
    private String subject = "";

    @Column(name = "body", nullable = false, columnDefinition = "LONGTEXT")
    private String body = "";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EmailMessageStatus status = EmailMessageStatus.DRAFT;

    @Column(name = "sent_at")
    private Instant sentAt;

    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="account_id") private EmailAccount account;
    @Column(name="provider_message_id",length=255) private String providerMessageId;
    @Column(name="provider_thread_id",length=255) private String providerThreadId;
    @Column(name="internet_message_id",length=998) private String internetMessageId;
    @Column(name="received_at") private Instant receivedAt;
    @Column(name="html_body",columnDefinition="LONGTEXT") private String htmlBody;
    @Column(name="external_sender_name", length=255) private String externalSenderName;
    @Column(name="external_sender_email", length=254) private String externalSenderEmail;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
