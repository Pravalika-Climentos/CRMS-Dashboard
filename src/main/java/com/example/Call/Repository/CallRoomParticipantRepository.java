package com.example.Call.Repository;

import com.example.Call.Entity.CallRoomParticipant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface CallRoomParticipantRepository
        extends JpaRepository<CallRoomParticipant, Long> {

    /*
     * Finds the user's current attendance record.
     *
     * leftAt = null means the participant is presently in
     * the room according to the backend.
     */
    @Query("""
        SELECT participant
        FROM CallRoomParticipant participant
        JOIN FETCH participant.room room
        JOIN FETCH participant.user
        WHERE room.roomId = :roomId
          AND participant.user.userId = :userId
          AND participant.leftAt IS NULL
        ORDER BY participant.joinedAt DESC
        """)
    List<CallRoomParticipant> findActiveAttendance(
            @Param("roomId") String roomId,
            @Param("userId") Long userId,
            Pageable pageable
    );

    /*
     * Lightweight check used before inserting another
     * attendance record.
     */
    boolean existsByRoomRoomIdAndUserUserIdAndLeftAtIsNull(
            String roomId,
            Long userId
    );

    /*
     * Number of users currently recorded as being inside
     * the room.
     */
    long countByRoomRoomIdAndLeftAtIsNull(
            String roomId
    );

    /*
     * Current participant listing for the team-room UI.
     */
    @Query("""
        SELECT participant
        FROM CallRoomParticipant participant
        JOIN FETCH participant.user user
        WHERE participant.room.roomId = :roomId
          AND participant.leftAt IS NULL
        ORDER BY
            CASE
                WHEN participant.participantRole =
                     com.example.Call.Entity.CallRoomParticipantRole.HOST
                THEN 0
                ELSE 1
            END,
            user.fullName ASC
        """)
    List<CallRoomParticipant> findActiveParticipants(
            @Param("roomId") String roomId
    );

    /*
     * Complete attendance history for one room.
     *
     * Rejoining produces another row, so the same user can
     * appear more than once in the historical result.
     */
    @Query(
        value = """
            SELECT participant
            FROM CallRoomParticipant participant
            JOIN FETCH participant.user
            WHERE participant.room.roomId = :roomId
            ORDER BY participant.joinedAt DESC
            """,
        countQuery = """
            SELECT COUNT(participant)
            FROM CallRoomParticipant participant
            WHERE participant.room.roomId = :roomId
            """
    )
    Page<CallRoomParticipant> findRoomAttendanceHistory(
            @Param("roomId") String roomId,
            Pageable pageable
    );

    /*
     * Latest attendance record for a user, whether active
     * or already closed.
     */
    @Query("""
        SELECT participant
        FROM CallRoomParticipant participant
        JOIN FETCH participant.room
        JOIN FETCH participant.user
        WHERE participant.room.roomId = :roomId
          AND participant.user.userId = :userId
        ORDER BY participant.joinedAt DESC
        """)
    List<CallRoomParticipant> findLatestAttendance(
            @Param("roomId") String roomId,
            @Param("userId") Long userId,
            Pageable pageable
    );

    /*
     * Marks the current user's open attendance record as
     * left.
     */
    @Modifying(
        clearAutomatically = true,
        flushAutomatically = true
    )
    @Query("""
        UPDATE CallRoomParticipant participant
        SET participant.leftAt = :leftAt
        WHERE participant.room.roomId = :roomId
          AND participant.user.userId = :userId
          AND participant.leftAt IS NULL
        """)
    int markUserAsLeft(
            @Param("roomId") String roomId,
            @Param("userId") Long userId,
            @Param("leftAt") Instant leftAt
    );

    /*
     * Closes all remaining attendance records when the host
     * ends or cancels the room.
     */
    @Modifying(
        clearAutomatically = true,
        flushAutomatically = true
    )
    @Query("""
        UPDATE CallRoomParticipant participant
        SET participant.leftAt = :leftAt
        WHERE participant.room.roomId = :roomId
          AND participant.leftAt IS NULL
        """)
    int closeAllActiveAttendance(
            @Param("roomId") String roomId,
            @Param("leftAt") Instant leftAt
    );

    /*
     * Used for authorization checks on ad-hoc rooms.
     */
    Optional<CallRoomParticipant>
        findFirstByRoomRoomIdAndUserUserIdOrderByJoinedAtDesc(
            String roomId,
            Long userId
        );
}