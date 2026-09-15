
package com.example.CRM.Service;

import com.example.Common.DTO.Response.PageResponse;
import com.example.Common.Exception.ConflictException;
import com.example.Common.Exception.ForbiddenOperationException;
import com.example.Common.Exception.ResourceNotFoundException;
import com.example.Common.Service.CurrentUserService;
import com.example.CRM.Entity.User;
import com.example.CRM.Repository.UserRepository;
import com.example.CRM.DTO.Request.AddTeamMembersRequest;
import com.example.CRM.DTO.Request.CreateTeamRequest;
import com.example.CRM.DTO.Request.UpdateTeamRequest;
import com.example.CRM.DTO.Response.TeamMemberResponse;
import com.example.CRM.DTO.Response.TeamMembershipResultResponse;
import com.example.CRM.DTO.Response.TeamResponse;
import com.example.CRM.Entity.Team;
import com.example.CRM.Entity.TeamMember;
import com.example.CRM.Entity.TeamMemberId;
import com.example.CRM.Mapper.TeamMapper;
import com.example.CRM.Repository.TeamMemberRepository;
import com.example.CRM.Repository.TeamRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamServiceImpl
        implements TeamService {

    private static final int MAX_TEAM_MEMBERS = 200;

    private final TeamRepository teamRepository;
    private final TeamMemberRepository
            teamMemberRepository;
    private final UserRepository userRepository;
    private final CurrentUserService
            currentUserService;
    private final TeamMapper teamMapper;

    @Override
    @Transactional
    public TeamResponse createTeam(
            CreateTeamRequest request
    ) {
        Long currentUserId =
                currentUserService
                        .getCurrentUserId();

        User owner = getActiveUser(
                currentUserId
        );

        LinkedHashSet<Long> requestedIds =
                new LinkedHashSet<>(
                        request.memberIds()
                );

        /*
         * The team owner is always a member.
         */
        requestedIds.add(currentUserId);

        validateMemberLimit(
                requestedIds.size()
        );

        Map<Long, User> users =
                loadActiveUsers(requestedIds);

        Team team = new Team();
        team.setName(request.name().trim());
        team.setDescription(
                normalizeNullable(
                        request.description()
                )
        );
        team.setOwner(owner);
        team.setActive(true);

        team = teamRepository.saveAndFlush(
                team
        );

        List<TeamMember> memberships =
                new ArrayList<>();

        for (Long userId : requestedIds) {
            TeamMember membership =
                    new TeamMember();

            membership.setId(
                    new TeamMemberId(
                            team.getTeamId(),
                            userId
                    )
            );

            membership.setTeam(team);
            membership.setUser(
                    users.get(userId)
            );
            membership.setAddedBy(owner);

            memberships.add(membership);
        }

        teamMemberRepository.saveAll(
                memberships
        );

        return teamMapper.toResponse(
                team,
                memberships.size(),
                currentUserId
        );
    }

    @Override
    public PageResponse<TeamResponse> getTeams(
            String search,
            String scope,
            int page,
            int size
    ) {
        validatePagination(page, size);

        Long currentUserId =
                currentUserService
                        .getCurrentUserId();

        String normalizedSearch =
                search == null
                        ? ""
                        : search.trim();

        if (normalizedSearch.length() > 150) {
            throw new IllegalArgumentException(
                    "Search cannot exceed 150 characters."
            );
        }

        String normalizedScope =
                scope == null
                        ? "available"
                        : scope.trim().toLowerCase();

        Pageable pageable =
                PageRequest.of(
                        page,
                        size,
                        Sort.by(
                            Sort.Direction.DESC,
                            "createdAt"
                        ).and(
                            Sort.by(
                                Sort.Direction.DESC,
                                "teamId"
                            )
                        )
                );

        Page<Team> teams;

        switch (normalizedScope) {
            case "available" ->
                teams =
                    teamRepository
                        .findByActiveTrueAndNameContainingIgnoreCase(
                            normalizedSearch,
                            pageable
                        );

            case "mine" ->
                teams =
                    teamRepository
                        .findByOwnerUserIdAndActiveAndNameContainingIgnoreCase(
                            currentUserId,
                            true,
                            normalizedSearch,
                            pageable
                        );

            case "member" ->
                teams =
                    teamRepository
                        .findActiveTeamsForMember(
                            currentUserId,
                            normalizedSearch,
                            pageable
                        );

            default ->
                throw new IllegalArgumentException(
                    "Invalid team scope."
                );
        }

        List<TeamResponse> responses =
                teams.getContent()
                     .stream()
                     .map(team ->
                         teamMapper.toResponse(
                             team,
                             teamMemberRepository
                                 .countByTeamTeamId(
                                     team.getTeamId()
                                 ),
                             currentUserId
                         )
                     )
                     .toList();

        return toPageResponse(
                teams,
                responses
        );
    }

    @Override
    public TeamResponse getTeam(
            Long teamId
    ) {
        Long currentUserId =
                currentUserService
                        .getCurrentUserId();

        Team team = getVisibleTeam(
                teamId,
                currentUserId
        );

        return teamMapper.toResponse(
                team,
                teamMemberRepository
                        .countByTeamTeamId(
                                teamId
                        ),
                currentUserId
        );
    }

    @Override
    public PageResponse<TeamMemberResponse>
            getMembers(
                    Long teamId,
                    int page,
                    int size
            ) {
        validatePagination(page, size);

        Long currentUserId =
                currentUserService
                        .getCurrentUserId();

        getVisibleTeam(
                teamId,
                currentUserId
        );

        Pageable pageable =
                PageRequest.of(
                        page,
                        size,
                        Sort.by(
                            "user.fullName"
                        ).ascending()
                         .and(
                            Sort.by(
                                "user.userId"
                            ).ascending()
                         )
                );

        Page<TeamMember> members =
                teamMemberRepository
                        .findByTeamTeamId(
                                teamId,
                                pageable
                        );

        List<TeamMemberResponse> responses =
                members.getContent()
                       .stream()
                       .map(
                           teamMapper
                               ::toMemberResponse
                       )
                       .toList();

        return toPageResponse(
                members,
                responses
        );
    }

    @Override
    @Transactional
    public TeamResponse updateTeam(
            Long teamId,
            UpdateTeamRequest request
    ) {
        Long currentUserId =
                currentUserService
                        .getCurrentUserId();

        Team team =
                getTeamForManagement(
                        teamId,
                        currentUserId
                );

        verifyVersion(
                request.getExpectedVersion(),
                team.getVersion()
        );

        if (!request.isNameProvided()
                && !request
                    .isDescriptionProvided()) {
            throw new IllegalArgumentException(
                    "Provide at least one field to update."
            );
        }

        if (request.isNameProvided()) {
            if (request.getName() == null
                    || request.getName()
                              .trim()
                              .isEmpty()) {
                throw new IllegalArgumentException(
                    "Team name cannot be empty."
                );
            }

            team.setName(
                    request.getName().trim()
            );
        }

        if (request.isDescriptionProvided()) {
            team.setDescription(
                    normalizeNullable(
                            request.getDescription()
                    )
            );
        }

        team = teamRepository.saveAndFlush(
                team
        );

        return teamMapper.toResponse(
                team,
                teamMemberRepository
                        .countByTeamTeamId(
                                teamId
                        ),
                currentUserId
        );
    }

    @Override
    @Transactional
    public TeamMembershipResultResponse
            addMembers(
                    Long teamId,
                    AddTeamMembersRequest request
            ) {
        Long currentUserId =
                currentUserService
                        .getCurrentUserId();

        Team team =
                getTeamForManagement(
                        teamId,
                        currentUserId
                );

        verifyVersion(
                request.expectedVersion(),
                team.getVersion()
        );

        User currentUser =
                getActiveUser(currentUserId);

        LinkedHashSet<Long> requestedIds =
                new LinkedHashSet<>(
                        request.userIds()
                );

        Map<Long, User> users =
                loadActiveUsers(requestedIds);

        List<String> addedIds =
                new ArrayList<>();

        List<String> existingIds =
                new ArrayList<>();

        for (Long userId : requestedIds) {
            boolean alreadyExists =
                    teamMemberRepository
                        .existsByTeamTeamIdAndUserUserId(
                            teamId,
                            userId
                        );

            if (alreadyExists) {
                existingIds.add(
                        userId.toString()
                );
                continue;
            }

            TeamMember member =
                    new TeamMember();

            member.setId(
                    new TeamMemberId(
                            teamId,
                            userId
                    )
            );
            member.setTeam(team);
            member.setUser(
                    users.get(userId)
            );
            member.setAddedBy(
                    currentUser
            );

            teamMemberRepository.save(member);

            addedIds.add(
                    userId.toString()
            );
        }

        long memberCount =
                teamMemberRepository
                        .countByTeamTeamId(
                                teamId
                        );

        validateMemberLimit(memberCount);

        /*
         * A no-op addition does not change
         * the team version.
         */
        if (!addedIds.isEmpty()) {
            incrementMembershipVersion(
                    teamId,
                    team.getVersion()
            );

            team = teamRepository
                    .findByIdForUpdate(teamId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                       "Team not found with ID: " + teamId
             ));
        }

        return new TeamMembershipResultResponse(
                teamId.toString(),
                memberCount,
                team.getVersion(),
                addedIds,
                existingIds,
                List.of()
        );
    }

    @Override
    @Transactional
    public TeamMembershipResultResponse
            removeMember(
                    Long teamId,
                    Long userId,
                    Long expectedVersion
            ) {
        Long currentUserId =
                currentUserService
                        .getCurrentUserId();

        Team team =
                getTeamForManagement(
                        teamId,
                        currentUserId
                );

        verifyVersion(
                expectedVersion,
                team.getVersion()
        );

        if (team.getOwner()
                .getUserId()
                .equals(userId)) {
            throw new ConflictException(
                    "TEAM_OWNER_REMOVAL_NOT_ALLOWED",
                    "The team owner cannot be removed."
            );
        }

        long deleted =
                teamMemberRepository
                    .deleteByTeamTeamIdAndUserUserId(
                        teamId,
                        userId
                    );

        if (deleted == 0) {
            throw new ResourceNotFoundException(
                    "Team member not found."
            );
        }

        incrementMembershipVersion(
                teamId,
                team.getVersion()
        );

        Team updatedTeam =
                teamRepository
                    .findByIdForUpdate(teamId)
                    .orElseThrow(
                        () ->
                            new ResourceNotFoundException(
                                "Team not found."
                            )
                    );

        long memberCount =
                teamMemberRepository
                    .countByTeamTeamId(teamId);

        return new TeamMembershipResultResponse(
                teamId.toString(),
                memberCount,
                updatedTeam.getVersion(),
                List.of(),
                List.of(),
                List.of(userId.toString())
        );
    }

    @Override
    @Transactional
    public TeamResponse retireTeam(
            Long teamId,
            Long expectedVersion
    ) {
        Long currentUserId =
                currentUserService
                        .getCurrentUserId();

        Team team =
                teamRepository
                    .findByIdForUpdate(teamId)
                    .orElseThrow(
                        () ->
                            new ResourceNotFoundException(
                                "Team not found."
                            )
                    );

        requireOwner(
                team,
                currentUserId
        );

        verifyVersion(
                expectedVersion,
                team.getVersion()
        );

        if (team.getActive()) {
            team.setActive(false);

            team = teamRepository
                    .saveAndFlush(team);
        }

        return teamMapper.toResponse(
                team,
                teamMemberRepository
                    .countByTeamTeamId(teamId),
                currentUserId
        );
    }

    private Team getTeamForManagement(
            Long teamId,
            Long currentUserId
    ) {
        Team team =
                teamRepository
                    .findByIdForUpdate(teamId)
                    .orElseThrow(
                        () ->
                            new ResourceNotFoundException(
                                "Team not found."
                            )
                    );

        requireOwner(
                team,
                currentUserId
        );

        if (!team.getActive()) {
            throw new ConflictException(
                    "TEAM_RETIRED",
                    "The team has been retired."
            );
        }

        return team;
    }

    private Team getVisibleTeam(
            Long teamId,
            Long currentUserId
    ) {
        Team team =
                teamRepository
                    .findById(teamId)
                    .orElseThrow(
                        () ->
                            new ResourceNotFoundException(
                                "Team not found."
                            )
                    );

        /*
         * Active teams are currently visible
         * to all active CRM users.
         *
         * Retired teams are visible only
         * to their owner.
         */
        if (!team.getActive()
                && !team.getOwner()
                        .getUserId()
                        .equals(currentUserId)) {
            throw new ResourceNotFoundException(
                    "Team not found."
            );
        }

        return team;
    }

    private void requireOwner(
            Team team,
            Long currentUserId
    ) {
        if (!team.getOwner()
                .getUserId()
                .equals(currentUserId)) {
            throw new ForbiddenOperationException(
                    "Only the team owner can manage this team."
            );
        }
    }

    private User getActiveUser(
            Long userId
    ) {
        User user =
                userRepository
                    .findById(userId)
                    .orElseThrow(
                        () ->
                            new ResourceNotFoundException(
                                "User not found."
                            )
                    );

        if (!Boolean.TRUE.equals(
                user.getActive()
        )) {
            throw new ForbiddenOperationException(
                    "The user is inactive."
            );
        }

        return user;
    }

    private Map<Long, User> loadActiveUsers(
            Set<Long> userIds
    ) {
        List<User> users =
                userRepository.findAllById(
                        userIds
                );

        Map<Long, User> usersById =
                users.stream()
                     .collect(
                         Collectors.toMap(
                             User::getUserId,
                             Function.identity()
                         )
                     );

        if (usersById.size()
                != userIds.size()) {
            throw new ResourceNotFoundException(
                    "One or more users were not found."
            );
        }

        for (User user : usersById.values()) {
            if (!Boolean.TRUE.equals(
                    user.getActive()
            )) {
                throw new ConflictException(
                    "USER_INACTIVE",
                    "Inactive users cannot be added to a team."
                );
            }
        }

        return usersById;
    }

    private void incrementMembershipVersion(
            Long teamId,
            Long expectedVersion
    ) {
        int updated =
                teamRepository
                    .incrementVersion(
                        teamId,
                        expectedVersion,
                        Instant.now()
                    );

        if (updated != 1) {
            throw new ConflictException(
                    "STALE_VERSION",
                    "The team was modified by another request."
            );
        }
    }

    private void verifyVersion(
            Long expectedVersion,
            Long currentVersion
    ) {
        if (!currentVersion.equals(
                expectedVersion
        )) {
            throw new ConflictException(
                    "STALE_VERSION",
                    "The team was modified by another request."
            );
        }
    }

    private void validateMemberLimit(
            long memberCount
    ) {
        if (memberCount
                > MAX_TEAM_MEMBERS) {
            throw new IllegalArgumentException(
                    "A team cannot contain more than 200 members."
            );
        }
    }

    private void validatePagination(
            int page,
            int size
    ) {
        if (page < 0) {
            throw new IllegalArgumentException(
                    "Page cannot be negative."
            );
        }

        if (size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Page size must be between 1 and 100."
            );
        }
    }

    private String normalizeNullable(
            String value
    ) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();

        return trimmed.isEmpty()
                ? null
                : trimmed;
    }

    private <S, T> PageResponse<T>
            toPageResponse(
                    Page<S> page,
                    List<T> items
            ) {
        return new PageResponse<>(
                items,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext()
        );
    }
}
