package com.example.Calendar.Entity;

import com.example.CRM.Entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(
    name = "calendar_event_participants",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_event_participant",
            columnNames = {
                "event_id",
                "user_id"
            }
        )
    },
    indexes = {
        @Index(
            name = "idx_participants_user_status",
            columnList = "user_id, status, event_id"
        ),
        @Index(
            name = "idx_participants_expiry",
            columnList = "status, expires_at"
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
public class CalendarEventParticipant {

    /*
     * This ID is also exposed as invitationId
     * through the Calendar API.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "participant_id")
    private Long participantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "event_id",
        nullable = false,
        foreignKey = @ForeignKey(
            name = "fk_participants_event"
        )
    )
    private CalendarEvent event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "user_id",
        nullable = false,
        foreignKey = @ForeignKey(
            name = "fk_participants_user"
        )
    )
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "status",
        nullable = false,
        length = 16
    )
    private InvitationStatus status =
            InvitationStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "invited_by",
        nullable = false,
        foreignKey = @ForeignKey(
            name = "fk_participants_inviter"
        )
    )
    private User invitedBy;

    /*
     * Invitation issue time.
     * Set explicitly by the service.
     */
    @Column(
        name = "invited_at",
        nullable = false
    )
    private Instant invitedAt;

    /*
     * Earlier of:
     * invitedAt + 5 hours
     * or
     * event start time
     */
    @Column(
        name = "expires_at",
        nullable = false
    )
    private Instant expiresAt;

    /*
     * Set only for ACCEPTED or DECLINED.
     */
    @Column(name = "responded_at")
    private Instant respondedAt;

    /*
     * Set only when status becomes REMOVED.
     */
    @Column(name = "removed_at")
    private Instant removedAt;

    /*
     * Must match the event's current
     * scheduleRevision when responding.
     */
    @Column(
        name = "schedule_revision",
        nullable = false
    )
    private Integer scheduleRevision = 1;

    /*
     * Prevents two browser windows from
     * responding to the same invitation.
     */
    @Version
    @Column(
        name = "version",
        nullable = false
    )
    private Long version = 0L;

    @CreationTimestamp
    @Column(
        name = "created_at",
        nullable = false,
        updatable = false
    )
    private Instant createdAt;

    @UpdateTimestamp
    @Column(
        name = "updated_at",
        nullable = false
    )
    private Instant updatedAt;
}
