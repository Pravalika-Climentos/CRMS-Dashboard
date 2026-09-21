package com.example.Email.Entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name="email_message_labels", uniqueConstraints=@UniqueConstraint(name="uq_email_message_label", columnNames={"message_id","user_id","label_id"}))
@Getter @Setter @NoArgsConstructor
public class EmailMessageLabel {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="message_label_id") private Long messageLabelId;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="message_id", nullable=false) private EmailMessage message;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="label_id", nullable=false) private EmailLabel label;
    @Column(name="user_id", nullable=false) private Long userId;
}
