package com.example.Calendar.Repository;

import com.example.Calendar.Entity.CalendarEventParticipant;
import com.example.Calendar.Entity.InvitationStatus;
import com.example.Calendar.Repository.Projection.EventParticipantCountProjection;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CalendarEventParticipantRepository
        extends JpaRepository<CalendarEventParticipant, Long> {

    Optional<CalendarEventParticipant>
    findByEventEventIdAndUserUserId(
            Long eventId,
            Long userId
    );

    boolean existsByEventEventIdAndUserUserId(
            Long eventId,
            Long userId
    );

    /*
     * Locks one participant/invitation before:
     * - accepting
     * - declining
     * - removing
     * - renewing
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT participant
            FROM CalendarEventParticipant participant
            JOIN FETCH participant.event event
            JOIN FETCH event.organizer
            LEFT JOIN FETCH event.category
            JOIN FETCH participant.user
            JOIN FETCH participant.invitedBy
            WHERE participant.participantId = :participantId
            """)
    Optional<CalendarEventParticipant>
    findByParticipantIdForUpdate(
            @Param("participantId") Long participantId
    );

    /*
     * Used when reading one invitation.
     * It also ensures that the invitation belongs to the
     * current user.
     */
    @EntityGraph(
        attributePaths = {
            "event",
            "event.organizer",
            "user",
            "invitedBy"
        }
    )
    Optional<CalendarEventParticipant>
    findByParticipantIdAndUserUserId(
            Long participantId,
            Long userId
    );

    /*
     * Active participants for one event.
     */
    @EntityGraph(
        attributePaths = {
            "event",
            "event.organizer",
            "user",
            "invitedBy"
        }
    )
    @Query(
        value = """
                SELECT participant
                FROM CalendarEventParticipant participant
                WHERE participant.event.eventId = :eventId
                  AND participant.status IN (
    com.example.Calendar.Entity.InvitationStatus.PENDING,
    com.example.Calendar.Entity.InvitationStatus.ACCEPTED
)
                ORDER BY participant.invitedAt ASC
                """,
        countQuery = """
                SELECT COUNT(participant)
                FROM CalendarEventParticipant participant
                WHERE participant.event.eventId = :eventId
                   AND participant.status IN (
    com.example.Calendar.Entity.InvitationStatus.PENDING,
    com.example.Calendar.Entity.InvitationStatus.ACCEPTED
)
                """
    )
    Page<CalendarEventParticipant> findActiveParticipants(
            @Param("eventId") Long eventId,
            Pageable pageable
    );

    /*
     * Participant count displayed in CalendarEventResponse.
     */
    long countByEventEventIdAndStatusNot(
            Long eventId,
            InvitationStatus excludedStatus
    );

    /*
     * Finds existing participant rows before adding people.
     */
    @EntityGraph(attributePaths = "user")
    List<CalendarEventParticipant>
    findAllByEventEventIdAndUserUserIdIn(
            Long eventId,
            Collection<Long> userIds
    );

    /*
     * Retrieves the current user's participant record for
     * several events without causing one query per event.
     */
    @EntityGraph(
        attributePaths = {
            "event",
            "event.organizer",
            "user",
            "invitedBy"
        }
    )
    List<CalendarEventParticipant>
    findAllByEventEventIdInAndUserUserId(
            Collection<Long> eventIds,
            Long userId
    );

    /*
     * Lists invitations using a selected status.
     */
    @EntityGraph(
        attributePaths = {
            "event",
            "event.organizer",
            "user",
            "invitedBy"
        }
    )
    Page<CalendarEventParticipant>
    findByUserUserIdAndStatusOrderByInvitedAtDesc(
            Long userId,
            InvitationStatus status,
            Pageable pageable
    );

    /*
     * Lists invitations when status=ALL.
     */
    @EntityGraph(
        attributePaths = {
            "event",
            "event.organizer",
            "user",
            "invitedBy"
        }
    )
    Page<CalendarEventParticipant>
    findByUserUserIdOrderByInvitedAtDesc(
            Long userId,
            Pageable pageable
    );

    /*
     * Invitation summary counts.
     */
    long countByUserUserIdAndStatus(
            Long userId,
            InvitationStatus status
    );

    /*
     * All non-removed participant records.
     * Used when an event schedule is changed and invitations
     * need to be renewed.
     */
    @EntityGraph(
        attributePaths = {
            "event",
            "user",
            "invitedBy"
        }
    )
    List<CalendarEventParticipant>
    findAllByEventEventIdAndStatusNot(
            Long eventId,
            InvitationStatus excludedStatus
    );

    @Query("""
        SELECT
            participant.event.eventId AS eventId,
            COUNT(participant.participantId) AS participantCount
        FROM CalendarEventParticipant participant
        WHERE participant.event.eventId IN :eventIds
           AND participant.status IN (
    com.example.Calendar.Entity.InvitationStatus.PENDING,
    com.example.Calendar.Entity.InvitationStatus.ACCEPTED
)
        GROUP BY participant.event.eventId
        """)
List<EventParticipantCountProjection>
findParticipantCountsByEventIds(
        @Param("eventIds")
        Collection<Long> eventIds
);

@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
        SELECT participant
        FROM CalendarEventParticipant participant
        JOIN FETCH participant.event event
        JOIN FETCH event.organizer
        JOIN FETCH participant.user
        JOIN FETCH participant.invitedBy
        WHERE event.eventId = :eventId
          AND participant.user.userId = :userId
        """)
Optional<CalendarEventParticipant>
findByEventIdAndUserIdForUpdate(
        @Param("eventId") Long eventId,
        @Param("userId") Long userId
);

@Modifying(
        flushAutomatically = true,
        clearAutomatically = false
)
@Query("""
        UPDATE CalendarEventParticipant participant
        SET participant.status =
                com.example.Calendar.Entity.InvitationStatus.EXPIRED,
            participant.version = participant.version + 1,
            participant.updatedAt = :now
        WHERE participant.user.userId = :userId
          AND participant.status =
                com.example.Calendar.Entity.InvitationStatus.PENDING
          AND participant.expiresAt <= :now
        """)
int expirePendingInvitations(
        @Param("userId") Long userId,
        @Param("now") Instant now
);

@EntityGraph(
    attributePaths = {
        "event",
        "event.organizer",
        "user",
        "invitedBy"
    }
)
@Query(
    value = """
            SELECT participant
            FROM CalendarEventParticipant participant
            WHERE participant.user.userId = :userId
              AND participant.event.organizer.userId <> :userId
              AND participant.status = :status
            ORDER BY participant.invitedAt DESC
            """,
    countQuery = """
            SELECT COUNT(participant)
            FROM CalendarEventParticipant participant
            WHERE participant.user.userId = :userId
              AND participant.event.organizer.userId <> :userId
              AND participant.status = :status
            """
)
Page<CalendarEventParticipant> findInvitationsByStatus(
        @Param("userId") Long userId,
        @Param("status") InvitationStatus status,
        Pageable pageable
);

@EntityGraph(
    attributePaths = {
        "event",
        "event.organizer",
        "user",
        "invitedBy"
    }
)
@Query(
    value = """
            SELECT participant
            FROM CalendarEventParticipant participant
            WHERE participant.user.userId = :userId
              AND participant.event.organizer.userId <> :userId
            ORDER BY participant.invitedAt DESC
            """,
    countQuery = """
            SELECT COUNT(participant)
            FROM CalendarEventParticipant participant
            WHERE participant.user.userId = :userId
              AND participant.event.organizer.userId <> :userId
            """
)
Page<CalendarEventParticipant> findAllInvitations(
        @Param("userId") Long userId,
        Pageable pageable
);

@Query("""
        SELECT COUNT(participant)
        FROM CalendarEventParticipant participant
        WHERE participant.user.userId = :userId
          AND participant.event.organizer.userId <> :userId
          AND participant.status = :status
        """)
long countInvitationsByStatus(
        @Param("userId") Long userId,
        @Param("status") InvitationStatus status
);

@EntityGraph(
    attributePaths = {
        "event",
        "event.organizer",
        "user"
    }
)
List<CalendarEventParticipant>
findAllByEventEventIdInAndUserUserIdInAndStatus(
        Collection<Long> eventIds,
        Collection<Long> userIds,
        InvitationStatus status
);
}