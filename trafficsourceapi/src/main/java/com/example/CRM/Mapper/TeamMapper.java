package com.example.CRM.Mapper;

import com.example.Common.DTO.Response.UserSummaryResponse;
import com.example.CRM.Entity.User;
import com.example.CRM.DTO.Response.TeamMemberResponse;
import com.example.CRM.DTO.Response.TeamPermissionsResponse;
import com.example.CRM.DTO.Response.TeamResponse;
import com.example.CRM.Entity.Team;
import com.example.CRM.Entity.TeamMember;

import org.springframework.stereotype.Component;

@Component
public class TeamMapper {

    public TeamResponse toResponse(
            Team team,
            long memberCount,
            Long currentUserId
    ) {
        boolean canManage =
                team.getActive()
                && team.getOwner()
                       .getUserId()
                       .equals(currentUserId);

        return new TeamResponse(
                team.getTeamId().toString(),
                team.getName(),
                team.getDescription(),
                toUserSummary(team.getOwner()),
                team.getActive(),
                memberCount,
                team.getVersion(),
                team.getCreatedAt(),
                team.getUpdatedAt(),
                new TeamPermissionsResponse(
                        canManage
                )
        );
    }

    public TeamMemberResponse toMemberResponse(
            TeamMember member
    ) {
        return new TeamMemberResponse(
                toUserSummary(member.getUser()),
                member.getAddedBy()
                      .getUserId()
                      .toString(),
                member.getJoinedAt()
        );
    }

    public UserSummaryResponse toUserSummary(
            User user
    ) {
        return new UserSummaryResponse(
                user.getUserId().toString(),
                user.getFullName(),
                user.getDesignation(),
                user.getAvatar(),
                user.getActive()
        );
    }
}