package com.example.Calendar.Entity;

import com.example.CRM.Entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
    name = "calendar_time_change_requests",
    indexes = {
        @Index(
            name = "idx_requests_event_status",
            columnList = "event_id, status"
        ),
        @Index(
            name = "idx_requests_requester",
            columnList = "requester_id, status"
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
public class CalendarTimeChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Long requestId;

    /*
     * The actual database also has a composite foreign key:
     *
     * (event_id, requester_id)
     *     -> calendar_event_participants(event_id, user_id)
     *
     * Therefore no additional foreign key should be generated
     * by Hibernate for these two individual relationships.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "event_id",
        nullable = false,
        foreignKey = @ForeignKey(
            value = ConstraintMode.NO_CONSTRAINT
        )
    )
    private CalendarEvent event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "requester_id",
        nullable = false,
        foreignKey = @ForeignKey(
            value = ConstraintMode.NO_CONSTRAINT
        )
    )
    private User requester;

    /*
     * The event schedule revision against
     * which this request was submitted.
     */
    @Column(
        name = "schedule_revision",
        nullable = false
    )
    private Integer scheduleRevision;

    @Column(
        name = "proposed_all_day",
        nullable = false
    )
    private Boolean proposedAllDay;

    /*
     * Used only for a proposed timed event.
     * Stored as UTC.
     */
    @Column(name = "proposed_start_at")
    private Instant proposedStartAt;

    @Column(name = "proposed_end_at")
    private Instant proposedEndAt;

    /*
     * Used only for a proposed all-day event.
     * proposedEndDate is exclusive.
     */
    @Column(name = "proposed_start_date")
    private LocalDate proposedStartDate;

    @Column(name = "proposed_end_date")
    private LocalDate proposedEndDate;

    @Column(
        name = "proposed_time_zone",
        nullable = false,
        length = 64
    )
    private String proposedTimeZone =
            "Asia/Kolkata";

    @Column(
        name = "reason",
        nullable = false,
        length = 2000
    )
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "status",
        nullable = false,
        length = 16
    )
    private TimeChangeRequestStatus status =
            TimeChangeRequestStatus.PENDING;

    /*
     * Populated only when the organizer
     * approves or rejects the request.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "decided_by",
        foreignKey = @ForeignKey(
            name = "fk_requests_decider"
        )
    )
    private User decidedBy;

    @Column(
        name = "decision_note",
        length = 2000
    )
    private String decisionNote;

    @Column(name = "decided_at")
    private Instant decidedAt;

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