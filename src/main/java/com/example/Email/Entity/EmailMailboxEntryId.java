package com.example.Email.Entity;
import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;

@Embeddable
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class EmailMailboxEntryId implements Serializable {
    @Column(name = "message_id") private Long messageId;
    @Column(name = "user_id") private Long userId;
}
