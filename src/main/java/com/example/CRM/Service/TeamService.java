package com.example.CRM.Service;

import com.example.Common.DTO.Response.PageResponse;
import com.example.CRM.DTO.Request.AddTeamMembersRequest;
import com.example.CRM.DTO.Request.CreateTeamRequest;
import com.example.CRM.DTO.Request.UpdateTeamRequest;
import com.example.CRM.DTO.Response.TeamMemberResponse;
import com.example.CRM.DTO.Response.TeamMembershipResultResponse;
import com.example.CRM.DTO.Response.TeamResponse;

public interface TeamService {

    TeamResponse createTeam(
            CreateTeamRequest request
    );

    PageResponse<TeamResponse> getTeams(
            String search,
            String scope,
            int page,
            int size
    );

    TeamResponse getTeam(
            Long teamId
    );

    PageResponse<TeamMemberResponse> getMembers(
            Long teamId,
            int page,
            int size
    );

    TeamResponse updateTeam(
            Long teamId,
            UpdateTeamRequest request
    );

    TeamMembershipResultResponse addMembers(
            Long teamId,
            AddTeamMembersRequest request
    );

    TeamMembershipResultResponse removeMember(
            Long teamId,
            Long userId,
            Long expectedVersion
    );

    TeamResponse retireTeam(
            Long teamId,
            Long expectedVersion
    );
}
