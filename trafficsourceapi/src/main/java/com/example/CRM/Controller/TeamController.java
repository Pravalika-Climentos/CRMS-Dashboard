package com.example.CRM.Controller;

import com.example.Common.DTO.Response.PageResponse;
import com.example.CRM.DTO.Request.AddTeamMembersRequest;
import com.example.CRM.DTO.Request.CreateTeamRequest;
import com.example.CRM.DTO.Request.UpdateTeamRequest;
import com.example.CRM.DTO.Response.TeamMemberResponse;
import com.example.CRM.DTO.Response.TeamMembershipResultResponse;
import com.example.CRM.DTO.Response.TeamResponse;
import com.example.CRM.Service.TeamService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
@Validated
public class TeamController {

    private final TeamService teamService;

    /*
     * Create a team.
     *
     * POST /api/teams
     */
    @PostMapping
    public ResponseEntity<TeamResponse> createTeam(
            @Valid @RequestBody CreateTeamRequest request
    ) {
        TeamResponse response = teamService.createTeam(request);

        URI location = URI.create("/api/teams/" + response.teamId());

        return ResponseEntity
                .created(location)
                .body(response);
    }

    /*
     * List available teams.
     *
     * GET /api/teams
     * GET /api/teams?search=sales
     * GET /api/teams?scope=mine
     * GET /api/teams?scope=member
     * GET /api/teams?active=true&page=0&size=20
     */
    @GetMapping
    public ResponseEntity<PageResponse<TeamResponse>> getTeams(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "available") String scope,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0")
            @PositiveOrZero int page,
            @RequestParam(defaultValue = "20")
            @Min(1) @Max(100) int size
    ) {
        return ResponseEntity.ok(
                teamService.getTeams(search, scope, page, size)
        );
    }

    /*
     * Get one team.
     *
     * GET /api/teams/10
     */
    @GetMapping("/{teamId}")
    public ResponseEntity<TeamResponse> getTeam(
            @PathVariable @Positive Long teamId
    ) {
        return ResponseEntity.ok(
                teamService.getTeam(teamId)
        );
    }

    /*
     * Update team name, description or active state.
     *
     * PATCH /api/teams/10
     */
    @PatchMapping("/{teamId}")
    public ResponseEntity<TeamResponse> updateTeam(
            @PathVariable @Positive Long teamId,
            @Valid @RequestBody UpdateTeamRequest request
    ) {
        return ResponseEntity.ok(
                teamService.updateTeam(teamId, request)
        );
    }

    /*
     * Retire a team.
     *
     * DELETE /api/teams/10?expectedVersion=3
     */
    @DeleteMapping("/{teamId}")
    public ResponseEntity<Void> retireTeam(
            @PathVariable @Positive Long teamId,
            @RequestParam @PositiveOrZero Long expectedVersion
    ) {
        teamService.retireTeam(teamId, expectedVersion);

        return ResponseEntity.noContent().build();
    }

    /*
     * List team members.
     *
     * GET /api/teams/10/members?page=0&size=20
     */
    @GetMapping("/{teamId}/members")
    public ResponseEntity<PageResponse<TeamMemberResponse>> getTeamMembers(
            @PathVariable @Positive Long teamId,
            @RequestParam(defaultValue = "0")
            @PositiveOrZero int page,
            @RequestParam(defaultValue = "20")
            @Min(1) @Max(100) int size
    ) {
        return ResponseEntity.ok(
                teamService.getMembers(teamId, page, size)
        );
    }

    /*
     * Add one or more users to a team.
     *
     * POST /api/teams/10/members
     */
    @PostMapping("/{teamId}/members")
    public ResponseEntity<TeamMembershipResultResponse> addTeamMembers(
            @PathVariable @Positive Long teamId,
            @Valid @RequestBody AddTeamMembersRequest request
    ) {
        return ResponseEntity.ok(
                teamService.addMembers(teamId, request)
        );
    }

    /*
     * Remove one member.
     *
     * DELETE /api/teams/10/members/5?expectedVersion=4
     */
    @DeleteMapping("/{teamId}/members/{userId}")
    public ResponseEntity<Void> removeTeamMember(
            @PathVariable @Positive Long teamId,
            @PathVariable @Positive Long userId,
            @RequestParam @PositiveOrZero Long expectedVersion
    ) {
        teamService.removeMember(teamId, userId, expectedVersion);

        return ResponseEntity.noContent().build();
    }
}