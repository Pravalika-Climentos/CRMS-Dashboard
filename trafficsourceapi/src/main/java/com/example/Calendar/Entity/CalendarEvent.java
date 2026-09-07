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
    name = "calendar_events",
    indexes = {
        @Index(
            name = "idx_events_organizer_time",
            columnList = "organizer_id, status, start_at"
        ),
        @Index(
            name = "idx_events_timed_range",
            columnList = "status, start_at, end_at"
        ),
        @Index(
            name = "idx_events_date_range",
            columnList = "status, start_date, end_date"
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
public class CalendarEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long eventId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "organizer_id",
        nullable = false,
        foreignKey = @ForeignKey(
            name = "fk_calendar_events_organizer"
        )
    )
    private User organizer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
    name = "category_id",
    foreignKey = @ForeignKey(
        name = "fk_calendar_events_category"
       )
    )
    private CalendarCategory category;

    @Column(
        name = "title",
        nullable = false,
        length = 200
    )
    private String title;

    @Column(
    name = "description",
    columnDefinition = "TEXT"
   )
   private String description;  

    @Column(
        name = "location",
        length = 255
    )
    private String location;

    @Column(
        name = "meeting_url",
        length = 2048
    )
    private String meetingUrl;

    @Column(
        name = "all_day",
        nullable = false
    )
    private Boolean allDay = false;

    /*
     * Used only for timed events.
     * Stored in the database as UTC.
     */
    @Column(name = "start_at")
    private Instant startAt;

    @Column(name = "end_at")
    private Instant endAt;

    /*
     * Used only for all-day events.
     * endDate is exclusive.
     *
     * Example:
     * startDate = 2026-09-03
     * endDate   = 2026-09-04
     * represents September 3 only.
     */
    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(
        name = "time_zone",
        nullable = false,
        length = 64
    )
    private String timeZone = "Asia/Kolkata";

    @Enumerated(EnumType.STRING)
    @Column(
        name = "visibility",
        nullable = false,
        length = 16
    )
    private CalendarVisibility visibility =
            CalendarVisibility.PRIVATE;

    @Column(
        name = "blocks_time",
        nullable = false
    )
    private Boolean blocksTime = true;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "status",
        nullable = false,
        length = 16
    )
    private CalendarEventStatus status =
            CalendarEventStatus.SCHEDULED;

    /*
     * Incremented only when the event schedule changes.
     * Used to invalidate old invitations.
     */
    @Column(
        name = "schedule_revision",
        nullable = false
    )
    private Integer scheduleRevision = 1;

    /*
     * Incremented automatically by JPA for every update.
     * Used to prevent stale edits.
     */
    @Version
    @Column(
        name = "version",
        nullable = false
    )
    private Long version = 0L;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

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
