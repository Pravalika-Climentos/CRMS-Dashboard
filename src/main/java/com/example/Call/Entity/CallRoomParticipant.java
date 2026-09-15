package com.example.Call.Entity;

import com.example.CRM.Entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
    name = "call_room_participants",
    indexes = {
        @Index(
            name = "idx_call_room_participant_room",
            columnList = "room_id, joined_at"
        ),
        @Index(
            name = "idx_call_room_participant_user",
            columnList = "user_id, joined_at"
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
public class CallRoomParticipant {

    @Id
    @GeneratedValue(
        strategy = GenerationType.IDENTITY
    )
    @Column(name = "participant_id")
    private Long participantId;

    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "room_id",
        nullable = false
    )
    private CallRoom room;

    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "user_id",
        nullable = false
    )
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "participant_role",
        nullable = false,
        length = 20
    )
    private CallRoomParticipantRole participantRole =
            CallRoomParticipantRole.MEMBER;

    @Column(
        name = "joined_at",
        nullable = false,
        updatable = false
    )
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    @PrePersist
    private void prepareForInsert() {

        if (joinedAt == null) {
            joinedAt = Instant.now();
        }

        if (participantRole == null) {
            participantRole =
                    CallRoomParticipantRole.MEMBER;
        }
    }
}