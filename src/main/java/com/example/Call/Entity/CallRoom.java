package com.example.Call.Entity;

import com.example.Common.Entity.BaseEntity;
import com.example.CRM.Entity.Team;
import com.example.CRM.Entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "call_rooms",
    indexes = {
        @Index(
            name = "idx_call_rooms_team",
            columnList = "team_id, started_at"
        ),
        @Index(
            name = "idx_call_rooms_creator",
            columnList = "created_by_user_id, started_at"
        ),
        @Index(
            name = "idx_call_rooms_status",
            columnList = "status, started_at"
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
public class CallRoom extends BaseEntity {

    @Id
    @Column(
        name = "room_id",
        nullable = false,
        length = 36,
        updatable = false
    )
    private String roomId;

    @Column(
        name = "room_name",
        nullable = false,
        length = 150
    )
    private String roomName;

    /*
     * A room may belong to a CRM team.
     *
     * It remains optional so the original module can still
     * create an ad-hoc group room when no team is selected.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "created_by_user_id",
        nullable = false
    )
    private User roomCreator;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "status",
        nullable = false,
        length = 30
    )
    private CallRoomStatus status =
            CallRoomStatus.OPEN;

    @Column(
        name = "started_at",
        nullable = false,
        updatable = false
    )
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    /*
     * Do not use @Lob here.
     *
     * In your Hibernate version, @Lob may validate String as
     * TINYTEXT instead of the existing MySQL TEXT column.
     */
    @Column(
        name = "notes",
        columnDefinition = "TEXT"
    )
    private String notes;

    @Column(
        name = "recording_path",
        length = 1024
    )
    private String recordingPath;

    @Version
    @Column(
        name = "version",
        nullable = false
    )
    private Long version;

    @PrePersist
    private void prepareForInsert() {

        if (
            roomId == null ||
            roomId.isBlank()
        ) {
            roomId =
                    UUID.randomUUID()
                        .toString();
        }

        if (startedAt == null) {
            startedAt = Instant.now();
        }

        if (status == null) {
            status = CallRoomStatus.OPEN;
        }
    }
}