package com.example.Chat.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ChatMessageUserStateId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "message_id")
    private Long messageId;

    @Column(name = "user_id")
    private Long userId;
}
