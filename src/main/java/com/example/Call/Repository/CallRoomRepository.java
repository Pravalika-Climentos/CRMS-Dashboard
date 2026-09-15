package com.example.Call.Repository;

import com.example.Call.Entity.CallRoom;
import com.example.Call.Entity.CallRoomStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CallRoomRepository
        extends JpaRepository<CallRoom, String> {

    /*
     * Returns a room only when the current user is allowed
     * to access it.
     *
     * Access is granted when the user:
     * 1. created the room,
     * 2. owns the linked team,
     * 3. belongs to the linked team, or
     * 4. previously joined the room.
     */
    @Query("""
        SELECT DISTINCT room
        FROM CallRoom room
        JOIN FETCH room.roomCreator
        LEFT JOIN FETCH room.team team
        WHERE room.roomId = :roomId
          AND (
                room.roomCreator.userId = :userId

                OR (
                    team IS NOT NULL
                    AND team.owner.userId = :userId
                )

                OR EXISTS (
                    SELECT teamMember.id
                    FROM TeamMember teamMember
                    WHERE teamMember.team = team
                      AND teamMember.user.userId = :userId
                )

                OR EXISTS (
                    SELECT participant.participantId
                    FROM CallRoomParticipant participant
                    WHERE participant.room = room
                      AND participant.user.userId = :userId
                )
          )
        """)
    Optional<CallRoom> findAccessibleRoom(
            @Param("roomId") String roomId,
            @Param("userId") Long userId
    );

    /*
     * Locks a room while someone joins, leaves, ends or
     * cancels it.
     *
     * The service must verify permission after obtaining
     * this lock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT room
        FROM CallRoom room
        JOIN FETCH room.roomCreator
        LEFT JOIN FETCH room.team
        WHERE room.roomId = :roomId
        """)
    Optional<CallRoom> findByRoomIdForUpdate(
            @Param("roomId") String roomId
    );

    /*
     * Finds the latest usable room with a given room name.
     *
     * This supports the original Call page behavior where
     * users enter a room name before joining.
     */
    @Query("""
        SELECT room
        FROM CallRoom room
        JOIN FETCH room.roomCreator
        LEFT JOIN FETCH room.team
        WHERE LOWER(room.roomName) =
              LOWER(:roomName)
          AND room.status IN :statuses
        ORDER BY room.startedAt DESC
        """)
    List<CallRoom> findUsableRoomsByName(
            @Param("roomName") String roomName,
            @Param("statuses")
            Collection<CallRoomStatus> statuses,
            Pageable pageable
    );

    /*
     * Lists rooms visible to the current user.
     *
     * This includes both team rooms and ad-hoc rooms.
     */
    @Query(
        value = """
            SELECT DISTINCT room
            FROM CallRoom room
            JOIN FETCH room.roomCreator
            LEFT JOIN FETCH room.team team
            WHERE room.roomCreator.userId = :userId

               OR (
                    team IS NOT NULL
                    AND team.owner.userId = :userId
               )

               OR EXISTS (
                    SELECT teamMember.id
                    FROM TeamMember teamMember
                    WHERE teamMember.team = team
                      AND teamMember.user.userId = :userId
               )

               OR EXISTS (
                    SELECT participant.participantId
                    FROM CallRoomParticipant participant
                    WHERE participant.room = room
                      AND participant.user.userId = :userId
               )

            ORDER BY room.startedAt DESC
            """,
        countQuery = """
            SELECT COUNT(DISTINCT room.roomId)
            FROM CallRoom room
            LEFT JOIN room.team team
            WHERE room.roomCreator.userId = :userId

               OR (
                    team IS NOT NULL
                    AND team.owner.userId = :userId
               )

               OR EXISTS (
                    SELECT teamMember.id
                    FROM TeamMember teamMember
                    WHERE teamMember.team = team
                      AND teamMember.user.userId = :userId
               )

               OR EXISTS (
                    SELECT participant.participantId
                    FROM CallRoomParticipant participant
                    WHERE participant.room = room
                      AND participant.user.userId = :userId
               )
            """
    )
    Page<CallRoom> findAccessibleRoomHistory(
            @Param("userId") Long userId,
            Pageable pageable
    );

    /*
     * Active/open rooms belonging to one CRM team.
     */
    @Query("""
        SELECT room
        FROM CallRoom room
        JOIN FETCH room.roomCreator
        JOIN FETCH room.team
        WHERE room.team.teamId = :teamId
          AND room.status IN :statuses
        ORDER BY room.startedAt DESC
        """)
    List<CallRoom> findTeamRoomsByStatus(
            @Param("teamId") Long teamId,
            @Param("statuses")
            Collection<CallRoomStatus> statuses
    );

    /*
     * Prevents accidentally creating multiple active rooms
     * for the same team.
     */
    boolean existsByTeamTeamIdAndStatusIn(
            Long teamId,
            Collection<CallRoomStatus> statuses
    );
}