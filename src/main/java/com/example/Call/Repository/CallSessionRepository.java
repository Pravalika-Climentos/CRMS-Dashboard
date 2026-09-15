package com.example.Call.Repository;

import com.example.Call.Entity.CallSession;
import com.example.Call.Entity.CallStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CallSessionRepository
        extends JpaRepository<CallSession, String> {

    /*
     * Returns one call only when the requested user is either
     * its caller or its recipient.
     *
     * This will be used by GET /api/calls/{callId}.
     */
    @Query("""
        SELECT call
        FROM CallSession call
        JOIN FETCH call.caller
        JOIN FETCH call.callee
        LEFT JOIN FETCH call.endedBy
        WHERE call.callId = :callId
          AND (
                call.caller.userId = :userId
                OR call.callee.userId = :userId
          )
        """)
    Optional<CallSession> findVisibleCall(
            @Param("callId") String callId,
            @Param("userId") Long userId
    );

    /*
     * Locks a call while its status is being changed.
     *
     * This prevents two simultaneous requests, such as Accept
     * and Decline, from updating the same ringing call.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT call
        FROM CallSession call
        JOIN FETCH call.caller
        JOIN FETCH call.callee
        LEFT JOIN FETCH call.endedBy
        WHERE call.callId = :callId
        """)
    Optional<CallSession> findByCallIdForUpdate(
            @Param("callId") String callId
    );

    /*
     * Complete call history for the logged-in user.
     *
     * If contactId is supplied, history is restricted to calls
     * between the logged-in user and that contact.
     */
    @Query(
        value = """
            SELECT call
            FROM CallSession call
            JOIN FETCH call.caller
            JOIN FETCH call.callee
            LEFT JOIN FETCH call.endedBy
            WHERE (
                    call.caller.userId = :userId
                    OR call.callee.userId = :userId
                  )
              AND (
                    :contactId IS NULL
                    OR (
                        call.caller.userId = :userId
                        AND call.callee.userId = :contactId
                    )
                    OR (
                        call.caller.userId = :contactId
                        AND call.callee.userId = :userId
                    )
                  )
            ORDER BY call.startedAt DESC
            """,
        countQuery = """
            SELECT COUNT(call)
            FROM CallSession call
            WHERE (
                    call.caller.userId = :userId
                    OR call.callee.userId = :userId
                  )
              AND (
                    :contactId IS NULL
                    OR (
                        call.caller.userId = :userId
                        AND call.callee.userId = :contactId
                    )
                    OR (
                        call.caller.userId = :contactId
                        AND call.callee.userId = :userId
                    )
                  )
            """
    )
    Page<CallSession> findCallHistory(
            @Param("userId") Long userId,
            @Param("contactId") Long contactId,
            Pageable pageable
    );

    /*
     * Finds active calls involving the given user.
     *
     * Active means RINGING or CONNECTED.
     */
    @Query("""
        SELECT call
        FROM CallSession call
        JOIN FETCH call.caller
        JOIN FETCH call.callee
        WHERE (
                call.caller.userId = :userId
                OR call.callee.userId = :userId
              )
          AND call.status IN :statuses
        ORDER BY call.startedAt DESC
        """)
    List<CallSession> findActiveCallsForUser(
            @Param("userId") Long userId,
            @Param("statuses")
            Collection<CallStatus> statuses
    );

    /*
     * A lightweight check used before starting a new call.
     *
     * It prevents the same user from being placed in multiple
     * active one-to-one calls.
     */
    @Query("""
        SELECT CASE
                   WHEN COUNT(call) > 0
                   THEN true
                   ELSE false
               END
        FROM CallSession call
        WHERE (
                call.caller.userId = :userId
                OR call.callee.userId = :userId
              )
          AND call.status IN :statuses
        """)
    boolean existsActiveCallForUser(
            @Param("userId") Long userId,
            @Param("statuses")
            Collection<CallStatus> statuses
    );

    /*
     * Missed incoming calls for the logged-in user.
     *
     * A call is missed only for its recipient, not its caller.
     */
    @Query(
        value = """
            SELECT call
            FROM CallSession call
            JOIN FETCH call.caller
            JOIN FETCH call.callee
            LEFT JOIN FETCH call.endedBy
            WHERE call.callee.userId = :userId
              AND call.status = :status
            ORDER BY call.startedAt DESC
            """,
        countQuery = """
            SELECT COUNT(call)
            FROM CallSession call
            WHERE call.callee.userId = :userId
              AND call.status = :status
            """
    )
    Page<CallSession> findIncomingCallsByStatus(
            @Param("userId") Long userId,
            @Param("status") CallStatus status,
            Pageable pageable
    );

    /*
     * Number of all missed calls for the user.
     *
     * This is a historical total, not an unread notification
     * count. An unread count would require an additional
     * viewed/read field.
     */
    long countByCalleeUserIdAndStatus(
            Long userId,
            CallStatus status
    );

    /*
     * Converts unanswered ringing calls to MISSED after the
     * configured ringing timeout.
     *
     * cutoff identifies old calls. endedAt records when the
     * cleanup operation processed them.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE CallSession call
        SET call.status = :missedStatus,
            call.endedAt = :endedAt,
            call.durationSeconds = 0
        WHERE call.status = :ringingStatus
          AND call.startedAt < :cutoff
        """)
    int markExpiredRingingCallsAsMissed(
            @Param("ringingStatus")
            CallStatus ringingStatus,

            @Param("missedStatus")
            CallStatus missedStatus,

            @Param("cutoff")
            Instant cutoff,

            @Param("endedAt")
            Instant endedAt
    );
}