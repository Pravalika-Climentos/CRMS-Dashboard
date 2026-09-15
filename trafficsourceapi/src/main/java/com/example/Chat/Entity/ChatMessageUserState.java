package com.example.Chat.Entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "chat_message_user_state",
    indexes = {
        @Index(
            name = "idx_chat_message_user_state_user",
            columnList = "user_id"
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
public class ChatMessageUserState {

    @EmbeddedId
    private ChatMessageUserStateId id;

    @Column(name = "is_favorite", nullable = false)
    private Boolean favorite = false;
}
