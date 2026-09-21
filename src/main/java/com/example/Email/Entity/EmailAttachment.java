package com.example.Email.Entity;

import com.example.CRM.Entity.User;
import com.example.Common.Entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "email_attachments", indexes = @Index(name = "idx_email_attachments_message", columnList = "message_id"))
@Getter @Setter @NoArgsConstructor
public class EmailAttachment extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attachment_id") private Long attachmentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private EmailMessage message;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by_user_id", nullable = false)
    private User uploadedBy;

    @Column(name = "original_filename", nullable = false, length = 255) private String originalFilename;
    @Column(name = "stored_filename", nullable = false, unique = true, length = 255) private String storedFilename;
    @Column(name = "content_type", nullable = false, length = 150) private String contentType;
    @Column(name = "file_size", nullable = false) private Long fileSize;
    @Column(name = "storage_path", nullable = false, length = 1024) private String storagePath;
}
