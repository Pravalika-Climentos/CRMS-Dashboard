package com.example.CRM.Repository;

import com.example.CRM.Entity.Team;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface TeamRepository
        extends JpaRepository<Team, Long> {

    Page<Team>
            findByActiveTrueAndNameContainingIgnoreCase(
                    String search,
                    Pageable pageable
            );

    Page<Team>
            findByOwnerUserIdAndActiveAndNameContainingIgnoreCase(
                    Long ownerId,
                    Boolean active,
                    String search,
                    Pageable pageable
            );

    @Query("""
        SELECT DISTINCT t
        FROM Team t
        JOIN TeamMember tm
            ON tm.team = t
        WHERE tm.user.userId = :userId
          AND t.active = true
          AND LOWER(t.name) LIKE
              LOWER(CONCAT('%', :search, '%'))
        """)
    Page<Team> findActiveTeamsForMember(
            @Param("userId")
            Long userId,

            @Param("search")
            String search,

            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT t
        FROM Team t
        WHERE t.teamId = :teamId
        """)
    Optional<Team> findByIdForUpdate(
            @Param("teamId")
            Long teamId
    );

    @Modifying(
        flushAutomatically = true,
        clearAutomatically = true
    )
    @Query("""
        UPDATE Team t
        SET t.version = t.version + 1,
            t.updatedAt = :updatedAt
        WHERE t.teamId = :teamId
          AND t.version = :expectedVersion
        """)
    int incrementVersion(
            @Param("teamId")
            Long teamId,

            @Param("expectedVersion")
            Long expectedVersion,

            @Param("updatedAt")
            Instant updatedAt
    );
}
