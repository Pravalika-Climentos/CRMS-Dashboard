package com.example.Calendar.Repository;

import com.example.Calendar.Entity.CalendarTimeChangeRequest;
import com.example.Calendar.Entity.TimeChangeRequestStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CalendarTimeChangeRequestRepository
        extends JpaRepository<
            CalendarTimeChangeRequest,
            Long
        > {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT r
        FROM CalendarTimeChangeRequest r
        WHERE r.requestId = :requestId
        """)
    Optional<CalendarTimeChangeRequest>
            findByIdForUpdate(
                    @Param("requestId")
                    Long requestId
            );

    Optional<CalendarTimeChangeRequest>
            findByEventEventIdAndRequesterUserIdAndScheduleRevisionAndStatus(
                    Long eventId,
                    Long requesterId,
                    Integer scheduleRevision,
                    TimeChangeRequestStatus status
            );

    List<CalendarTimeChangeRequest>
            findByEventEventIdAndStatus(
                    Long eventId,
                    TimeChangeRequestStatus status
            );

    Page<CalendarTimeChangeRequest>
            findByRequesterUserId(
                    Long requesterId,
                    Pageable pageable
            );

    Page<CalendarTimeChangeRequest>
            findByRequesterUserIdAndStatus(
                    Long requesterId,
                    TimeChangeRequestStatus status,
                    Pageable pageable
            );

    @Query("""
        SELECT r
        FROM CalendarTimeChangeRequest r
        WHERE r.event.organizer.userId = :organizerId
        ORDER BY r.createdAt DESC
        """)
    Page<CalendarTimeChangeRequest>
            findReceivedByOrganizer(
                    @Param("organizerId")
                    Long organizerId,
                    Pageable pageable
            );

    @Query("""
        SELECT r
        FROM CalendarTimeChangeRequest r
        WHERE r.event.organizer.userId =
              :organizerId
          AND r.status = :status
        ORDER BY r.createdAt DESC
        """)
    Page<CalendarTimeChangeRequest>
            findReceivedByOrganizerAndStatus(
                    @Param("organizerId")
                    Long organizerId,

                    @Param("status")
                    TimeChangeRequestStatus status,

                    Pageable pageable
            );

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE CalendarTimeChangeRequest r
        SET r.status = :obsoleteStatus
        WHERE r.event.eventId = :eventId
          AND r.status = :pendingStatus
        """)
    int markPendingRequestsObsolete(
            @Param("eventId")
            Long eventId,

            @Param("pendingStatus")
            TimeChangeRequestStatus pendingStatus,

            @Param("obsoleteStatus")
            TimeChangeRequestStatus obsoleteStatus
    );

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE CalendarTimeChangeRequest r
        SET r.status = :obsoleteStatus
        WHERE r.event.eventId = :eventId
          AND r.requester.userId = :requesterId
          AND r.status = :pendingStatus
        """)
    int markParticipantRequestsObsolete(
            @Param("eventId")
            Long eventId,

            @Param("requesterId")
            Long requesterId,

            @Param("pendingStatus")
            TimeChangeRequestStatus pendingStatus,

            @Param("obsoleteStatus")
            TimeChangeRequestStatus obsoleteStatus
    );

    @Modifying(flushAutomatically = true)
@Query("""
        UPDATE CalendarTimeChangeRequest request
        SET request.status = :obsoleteStatus,
            request.version = request.version + 1,
            request.updatedAt = :now
        WHERE request.event.eventId = :eventId
          AND request.requestId <> :excludedRequestId
          AND request.status = :pendingStatus
        """)
int markOtherPendingRequestsObsolete(
        @Param("eventId") Long eventId,
        @Param("excludedRequestId") Long excludedRequestId,
        @Param("pendingStatus")
        TimeChangeRequestStatus pendingStatus,
        @Param("obsoleteStatus")
        TimeChangeRequestStatus obsoleteStatus,
        @Param("now") Instant now
);
}
