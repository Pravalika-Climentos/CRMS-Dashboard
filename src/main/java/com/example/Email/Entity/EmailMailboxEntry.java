package com.example.Email.Entity;

import com.example.CRM.Entity.User;
import com.example.Common.Entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "email_mailbox_entries", indexes = {
    @Index(name = "idx_email_mailbox_user_trash", columnList = "user_id,trashed_at"),
    @Index(name = "idx_email_mailbox_user_flags", columnList = "user_id,is_read,is_starred,is_important"),
    @Index(name = "idx_email_mailbox_user_role", columnList = "user_id,mailbox_role")
})
@Getter @Setter @NoArgsConstructor
public class EmailMailboxEntry extends BaseEntity {
    @EmbeddedId private EmailMailboxEntryId id;

    @MapsId("messageId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private EmailMessage message;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "mailbox_role", nullable = false, length = 20)
    private EmailMailboxRole mailboxRole;

    @Column(name = "is_read", nullable = false) private Boolean read = false;
    @Column(name = "is_starred", nullable = false) private Boolean starred = false;
    @Column(name = "is_important", nullable = false) private Boolean important = false;
    @Column(name = "is_archived", nullable = false) private Boolean archived = false;
    @Column(name = "is_spam", nullable = false) private Boolean spam = false;
    @Column(name = "trashed_at") private Instant trashedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "custom_folder_id")
    private EmailCustomFolder customFolder;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
