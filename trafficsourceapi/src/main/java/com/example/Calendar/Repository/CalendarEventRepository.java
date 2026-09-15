package com.example.Calendar.Repository;

import com.example.Calendar.Entity.CalendarEvent;
import com.example.Calendar.Entity.CalendarEventStatus;
import com.example.Calendar.Repository.Projection.CalendarAvailabilityEventProjection;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CalendarEventRepository
        extends JpaRepository<CalendarEvent, Long> {

    /*
     * Loads one event together with its organizer.
     * Used for GET /api/calendar/events/{eventId}.
     */
    @EntityGraph(attributePaths = {"organizer","category"})
    Optional<CalendarEvent> findByEventId(
            Long eventId
    );

    /*
     * Locks an event while it is being edited,
     * cancelled or having participants changed.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT event
            FROM CalendarEvent event
            JOIN FETCH event.organizer
            WHERE event.eventId = :eventId
            """)
    Optional<CalendarEvent> findByEventIdForUpdate(
            @Param("eventId") Long eventId
    );

    /*
     * Returns events visible to one user inside a calendar range.
     *
     * A visible event is one where the user is:
     * - the organizer, or
     * - an active participant/invitee.
     *
     * Overlap condition:
     * event start < requested end
     * event end   > requested start
     */
    @Query("""
            SELECT DISTINCT event
            FROM CalendarEvent event
            JOIN FETCH event.organizer
            LEFT JOIN CalendarEventParticipant participant
                ON participant.event = event
                AND participant.user.userId = :userId
                 AND participant.status IN (
                     InvitationStatus.PENDING,
                     InvitationStatus.ACCEPTED
                    )
            WHERE event.status = :status
              AND (
                    event.organizer.userId = :userId
                    OR participant.participantId IS NOT NULL
              )
              AND (
                    (
                        event.allDay = false
                        AND event.startAt < :toInstant
                        AND event.endAt > :fromInstant
                    )
                    OR
                    (
                        event.allDay = true
                        AND event.startDate < :toDate
                        AND event.endDate > :fromDate
                    )
              )
            """)
    List<CalendarEvent> findVisibleEventsInRange(
            @Param("userId") Long userId,
            @Param("status") CalendarEventStatus status,
            @Param("fromInstant") Instant fromInstant,
            @Param("toInstant") Instant toInstant,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    /*
     * Upcoming timed events.
     *
     * The service will combine these with upcoming all-day
     * events and sort the final result chronologically.
     */
    @Query(
        value = """
                SELECT DISTINCT event
                FROM CalendarEvent event
                JOIN FETCH event.organizer
                LEFT JOIN CalendarEventParticipant participant
                    ON participant.event = event
                    AND participant.user.userId = :userId
                     AND participant.status IN (
                     InvitationStatus.PENDING,
                     InvitationStatus.ACCEPTED
                    )
                WHERE event.status = :status
                  AND event.allDay = false
                  AND event.endAt > :now
                  AND (
                        event.organizer.userId = :userId
                        OR participant.participantId IS NOT NULL
                  )
                ORDER BY event.startAt ASC
                """,
        countQuery = """
                SELECT COUNT(DISTINCT event.eventId)
                FROM CalendarEvent event
                LEFT JOIN CalendarEventParticipant participant
                    ON participant.event = event
                    AND participant.user.userId = :userId
                    AND participant.status IN (
                     InvitationStatus.PENDING,
                     InvitationStatus.ACCEPTED
                    )
                WHERE event.status = :status
                  AND event.allDay = false
                  AND event.endAt > :now
                  AND (
                        event.organizer.userId = :userId
                        OR participant.participantId IS NOT NULL
                  )
                """
    )
    Page<CalendarEvent> findUpcomingTimedEvents(
            @Param("userId") Long userId,
            @Param("status") CalendarEventStatus status,
            @Param("now") Instant now,
            Pageable pageable
    );

    /*
     * Upcoming all-day events.
     */
    @Query(
        value = """
                SELECT DISTINCT event
                FROM CalendarEvent event
                JOIN FETCH event.organizer
                LEFT JOIN CalendarEventParticipant participant
                    ON participant.event = event
                    AND participant.user.userId = :userId
                     AND participant.status IN (
                     InvitationStatus.PENDING,
                     InvitationStatus.ACCEPTED
                    )
                WHERE event.status = :status
                  AND event.allDay = true
                  AND event.endDate > :today
                  AND (
                        event.organizer.userId = :userId
                        OR participant.participantId IS NOT NULL
                  )
                ORDER BY event.startDate ASC
                """,
        countQuery = """
                SELECT COUNT(DISTINCT event.eventId)
                FROM CalendarEvent event
                LEFT JOIN CalendarEventParticipant participant
                    ON participant.event = event
                    AND participant.user.userId = :userId
                    AND participant.status IN (
                     InvitationStatus.PENDING,
                     InvitationStatus.ACCEPTED
                    )
                WHERE event.status = :status
                  AND event.allDay = true
                  AND event.endDate > :today
                  AND (
                        event.organizer.userId = :userId
                        OR participant.participantId IS NOT NULL
                  )
                """
    )
    Page<CalendarEvent> findUpcomingAllDayEvents(
            @Param("userId") Long userId,
            @Param("status") CalendarEventStatus status,
            @Param("today") LocalDate today,
            Pageable pageable
    );

    /*
     * Personal timed calendar for multiple users.
     * Used later by team-calendar and availability services.
     */
    @Query("""
            SELECT DISTINCT event
            FROM CalendarEvent event
            JOIN FETCH event.organizer
            LEFT JOIN CalendarEventParticipant participant
                ON participant.event = event
            WHERE event.status = :status
              AND event.blocksTime = true
              AND event.allDay = false
              AND event.startAt < :toInstant
              AND event.endAt > :fromInstant
              AND (
                    event.organizer.userId IN :userIds
                    OR (
                        participant.user.userId IN :userIds
                        AND participant.status =
                            com.example.Calendar.Entity.InvitationStatus.ACCEPTED
                    )
              )
            """)
    List<CalendarEvent> findBlockingTimedEventsForUsers(
            @Param("userIds") List<Long> userIds,
            @Param("status") CalendarEventStatus status,
            @Param("fromInstant") Instant fromInstant,
            @Param("toInstant") Instant toInstant
    );

    /*
     * Personal all-day calendar for multiple users.
     */
    @Query("""
            SELECT DISTINCT event
            FROM CalendarEvent event
            JOIN FETCH event.organizer
            LEFT JOIN CalendarEventParticipant participant
                ON participant.event = event
            WHERE event.status = :status
              AND event.blocksTime = true
              AND event.allDay = true
              AND event.startDate < :toDate
              AND event.endDate > :fromDate
              AND (
                    event.organizer.userId IN :userIds
                    OR (
                        participant.user.userId IN :userIds
                        AND participant.status =
                            com.example.Calendar.Entity.InvitationStatus.ACCEPTED
                    )
              )
            """)
    List<CalendarEvent> findBlockingAllDayEventsForUsers(
            @Param("userIds") List<Long> userIds,
            @Param("status") CalendarEventStatus status,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    @Query("""
        SELECT
            event.organizer.userId AS userId,
            event.eventId AS eventId,
            event.organizer.userId AS organizerId,
            event.title AS title,
            event.visibility AS visibility,
            event.allDay AS allDay,
            event.startAt AS startAt,
            event.endAt AS endAt,
            event.startDate AS startDate,
            event.endDate AS endDate,
            event.timeZone AS timeZone,
            NULL AS participationStatus,
            NULL AS invitationExpiresAt
        FROM CalendarEvent event
        WHERE event.organizer.userId IN :userIds
          AND event.status =
              com.example.Calendar.Entity.CalendarEventStatus.SCHEDULED
          AND event.blocksTime = true
          AND (
                :excludeEventId IS NULL
                OR event.eventId <> :excludeEventId
          )
          AND (
                (
                    event.allDay = false
                    AND event.startAt < :toInstant
                    AND event.endAt > :fromInstant
                )
                OR
                (
                    event.allDay = true
                    AND event.startDate < :toDate
                    AND event.endDate > :fromDate
                )
          )
        """)
List<CalendarAvailabilityEventProjection>
findOrganizerAvailabilityEvents(
        @Param("userIds")
        Collection<Long> userIds,

        @Param("fromInstant")
        Instant fromInstant,

        @Param("toInstant")
        Instant toInstant,

        @Param("fromDate")
        LocalDate fromDate,

        @Param("toDate")
        LocalDate toDate,

        @Param("excludeEventId")
        Long excludeEventId
);

@Query("""
        SELECT
            participant.user.userId AS userId,
            event.eventId AS eventId,
            event.organizer.userId AS organizerId,
            event.title AS title,
            event.visibility AS visibility,
            event.allDay AS allDay,
            event.startAt AS startAt,
            event.endAt AS endAt,
            event.startDate AS startDate,
            event.endDate AS endDate,
            event.timeZone AS timeZone,
            participant.status AS participationStatus,
            participant.expiresAt AS invitationExpiresAt
        FROM CalendarEventParticipant participant
        JOIN participant.event event
        WHERE participant.user.userId IN :userIds
          AND participant.user.userId <>
              event.organizer.userId
          AND event.status =
              com.example.Calendar.Entity.CalendarEventStatus.SCHEDULED
          AND event.blocksTime = true
          AND participant.scheduleRevision =
              event.scheduleRevision
          AND (
                participant.status =
                    com.example.Calendar.Entity.InvitationStatus.ACCEPTED
                OR
                (
                    participant.status =
                        com.example.Calendar.Entity.InvitationStatus.PENDING
                    AND participant.expiresAt > :now
                )
          )
          AND (
                :excludeEventId IS NULL
                OR event.eventId <> :excludeEventId
          )
          AND (
                (
                    event.allDay = false
                    AND event.startAt < :toInstant
                    AND event.endAt > :fromInstant
                )
                OR
                (
                    event.allDay = true
                    AND event.startDate < :toDate
                    AND event.endDate > :fromDate
                )
          )
        """)
List<CalendarAvailabilityEventProjection>
findParticipantAvailabilityEvents(
        @Param("userIds")
        Collection<Long> userIds,

        @Param("fromInstant")
        Instant fromInstant,

        @Param("toInstant")
        Instant toInstant,

        @Param("fromDate")
        LocalDate fromDate,

        @Param("toDate")
        LocalDate toDate,

        @Param("now")
        Instant now,

        @Param("excludeEventId")
        Long excludeEventId
);


}