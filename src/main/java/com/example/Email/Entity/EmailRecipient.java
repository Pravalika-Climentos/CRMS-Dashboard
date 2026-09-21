package com.example.Email.Entity;

import com.example.CRM.Entity.User;
import com.example.Common.Entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "email_recipients",
    uniqueConstraints = @UniqueConstraint(name = "uq_email_recipient_message_address", columnNames = {"message_id", "email_address"}),
    indexes = {
        @Index(name = "idx_email_recipients_user_message", columnList = "recipient_user_id,message_id"),
        @Index(name = "idx_email_recipients_message_type", columnList = "message_id,recipient_type")
    })
@Getter @Setter @NoArgsConstructor
public class EmailRecipient extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recipient_id")
    private Long recipientId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private EmailMessage message;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_user_id")
    private User recipientUser;

    @Column(name = "email_address", nullable = false, length = 254)
    private String emailAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_type", nullable = false, length = 10)
    private EmailRecipientType recipientType;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 20)
    private EmailDeliveryStatus deliveryStatus = EmailDeliveryStatus.PENDING;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;
}
