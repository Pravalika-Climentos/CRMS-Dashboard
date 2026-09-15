package com.example.CRM.Repository;

import com.example.CRM.Entity.TeamMember;
import com.example.CRM.Entity.TeamMemberId;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TeamMemberRepository
        extends JpaRepository<
            TeamMember,
            TeamMemberId
        > {

    Optional<TeamMember>
            findByTeamTeamIdAndUserUserId(
                    Long teamId,
                    Long userId
            );

    boolean existsByTeamTeamIdAndUserUserId(
            Long teamId,
            Long userId
    );

    long countByTeamTeamId(
            Long teamId
    );

    Page<TeamMember> findByTeamTeamId(
            Long teamId,
            Pageable pageable
    );

    List<TeamMember> findAllByTeamTeamId(
            Long teamId
    );

    @Query("""
        SELECT tm
        FROM TeamMember tm
        JOIN FETCH tm.team t
        JOIN FETCH tm.user u
        WHERE t.teamId IN :teamIds
        ORDER BY t.teamId, u.userId
        """)
    List<TeamMember> findAllWithUsersByTeamIds(
            @Param("teamIds")
            Collection<Long> teamIds
    );

    long deleteByTeamTeamIdAndUserUserId(
            Long teamId,
            Long userId
    );
}