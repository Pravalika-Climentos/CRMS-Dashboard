package com.example.Calendar.Service;

import com.example.CRM.Entity.Team;
import com.example.CRM.Entity.TeamMember;
import com.example.CRM.Entity.User;
import com.example.CRM.Repository.TeamMemberRepository;
import com.example.CRM.Repository.TeamRepository;
import com.example.CRM.Repository.UserRepository;
import com.example.Calendar.DTO.Request.AddEventParticipantsRequest;
import com.example.Calendar.DTO.Request.CreateCalendarEventRequest;
import com.example.Calendar.DTO.Request.UpdateCalendarEventRequest;
import com.example.Calendar.DTO.Response.CalendarEventParticipantResponse;
import com.example.Calendar.DTO.Response.CalendarEventResponse;
import com.example.Calendar.DTO.Response.ParticipantMutationResponse;
import com.example.Calendar.Entity.CalendarCategory;
import com.example.Calendar.Entity.CalendarEvent;
import com.example.Calendar.Entity.CalendarEventParticipant;
import com.example.Calendar.Entity.CalendarEventStatus;
import com.example.Calendar.Entity.CalendarVisibility;
import com.example.Calendar.Entity.InvitationStatus;
import com.example.Calendar.Entity.TimeChangeRequestStatus;
import com.example.Calendar.Mapper.CalendarEventMapper;
import com.example.Calendar.Repository.CalendarCategoryRepository;
import com.example.Calendar.Repository.CalendarEventParticipantRepository;
import com.example.Calendar.Repository.CalendarEventRepository;
import com.example.Calendar.Repository.Projection.EventParticipantCountProjection;
import com.example.Calendar.Validation.CalendarScheduleValidator;
import com.example.Calendar.Validation.ValidatedCalendarSchedule;
import com.example.Common.DTO.Response.PageResponse;
import com.example.Common.Exception.ConflictException;
import com.example.Common.Exception.ForbiddenOperationException;
import com.example.Common.Exception.ResourceNotFoundException;
import com.example.Common.Service.CurrentUserService;
import com.example.Calendar.Repository.CalendarTimeChangeRequestRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarEventServiceImpl
        implements CalendarEventService {

    private static final long INVITATION_VALIDITY_HOURS = 5L;

    private final CalendarEventRepository
            calendarEventRepository;

    private final CalendarEventParticipantRepository
            participantRepository;

    private final UserRepository userRepository;

    private final TeamRepository teamRepository;

    private final TeamMemberRepository
            teamMemberRepository;

    private final CurrentUserService currentUserService;

    private final CalendarScheduleValidator
            scheduleValidator;

    private final CalendarEventMapper calendarEventMapper;

    private final CalendarTimeChangeRequestRepository
        timeChangeRequestRepository;

    private final CalendarCategoryRepository
        categoryRepository;

    @Override
    @Transactional
    public CalendarEventResponse createEvent(
            CreateCalendarEventRequest request
    ) {
        Long currentUserId =
                currentUserService.getCurrentUserId();

        User organizer =
                getActiveUser(
                        currentUserId,
                        "Current user was not found or is inactive."
                );

        ValidatedCalendarSchedule schedule =
                scheduleValidator.validateForCreate(request);

        Set<Long> expandedParticipantIds =
                expandParticipantIds(
                        request.inviteeUserIds(),
                        request.teamIds()
                );

        /*
         * The organizer is always added separately as an
         * ACCEPTED participant.
         */
        expandedParticipantIds.remove(currentUserId);

        List<User> invitedUsers =
                loadActiveUsers(
                        expandedParticipantIds
                );

        Instant now = Instant.now();

        Instant eventStart =
                getEventStartInstant(schedule);

        /*
         * Invitations cannot be valid if the event has
         * already started.
         */
        if (!invitedUsers.isEmpty()
                && !eventStart.isAfter(now)) {
            throw new IllegalArgumentException(
                    "Participants cannot be invited to an event "
                    + "that has already started."
            );
        }

        CalendarEvent event =
                buildEvent(
                        request,
                        organizer,
                        schedule
                );

        event = calendarEventRepository.saveAndFlush(event);

        CalendarEventParticipant organizerParticipant =
                createOrganizerParticipant(
                        event,
                        organizer,
                        now
                );

        List<CalendarEventParticipant> participants =
                new ArrayList<>();

        participants.add(organizerParticipant);

        Instant invitationExpiry =
                calculateInvitationExpiry(
                        now,
                        eventStart
                );

        for (User invitedUser : invitedUsers) {
            participants.add(
                    createPendingParticipant(
                            event,
                            invitedUser,
                            organizer,
                            now,
                            invitationExpiry
                    )
            );
        }

        participantRepository.saveAll(participants);
        participantRepository.flush();

        return calendarEventMapper.toResponse(
                event,
                participants.size(),
                currentUserId,
                organizerParticipant
        );
    }

    private CalendarEvent buildEvent(
            CreateCalendarEventRequest request,
            User organizer,
            ValidatedCalendarSchedule schedule
    ) {
        CalendarEvent event =
                new CalendarEvent();

        event.setOrganizer(organizer);
        event.setCategory(
        resolveEventCategory(
                request.categoryId()        )
);
        event.setTitle(
                normalizeRequiredText(
                        request.title(),
                        "Event title is required."
                )
        );

        event.setDescription(
                normalizeOptionalText(
                        request.description()
                )
        );

        event.setLocation(
                normalizeOptionalText(
                        request.location()
                )
        );

        event.setMeetingUrl(
                normalizeOptionalText(
                        request.meetingUrl()
                )
        );

        event.setVisibility(
                request.visibility() == null
                        ? CalendarVisibility.PRIVATE
                        : request.visibility()
        );

        event.setBlocksTime(
                request.blocksTime() == null
                        ? true
                        : request.blocksTime()
        );

        event.setStatus(
                CalendarEventStatus.SCHEDULED
        );

        event.setScheduleRevision(1);

        calendarEventMapper.applySchedule(
                event,
                schedule
        );

        return event;
    }

    private Set<Long> expandParticipantIds(
            Set<Long> requestedUserIds,
            Set<Long> requestedTeamIds
    ) {
        Set<Long> participantIds =
                new LinkedHashSet<>();

        if (requestedUserIds != null) {
            participantIds.addAll(
                    requestedUserIds
            );
        }

        if (requestedTeamIds == null
                || requestedTeamIds.isEmpty()) {
            return participantIds;
        }

        Set<Long> uniqueTeamIds =
                new LinkedHashSet<>(
                        requestedTeamIds
                );

        validateActiveTeams(uniqueTeamIds);

        List<TeamMember> teamMembers =
                teamMemberRepository
                        .findAllWithUsersByTeamIds(
                                uniqueTeamIds
                        );

        for (TeamMember teamMember : teamMembers) {
            User user = teamMember.getUser();

            if (user != null
                    && Boolean.TRUE.equals(
                            user.getActive()
                    )) {
                participantIds.add(
                        user.getUserId()
                );
            }
        }

        return participantIds;
    }

  private CalendarCategory resolveEventCategory(
        Long categoryId
) {
    if (categoryId == null) {
        return null;
    }

    CalendarCategory category =
            categoryRepository.findById(categoryId)
                    .orElseThrow(
                            () ->
                                new ResourceNotFoundException(
                                    "Calendar category not found."
                                )
                    );

    if (!Boolean.TRUE.equals(category.getActive())) {
        throw new ConflictException(
                "CATEGORY_INACTIVE",
                "An inactive category cannot be assigned to an event."
        );
    }

    return category;
}

    private void validateActiveTeams(
            Set<Long> requestedTeamIds
    ) {
        List<Team> teams =
                teamRepository.findAllById(
                        requestedTeamIds
                );

        Map<Long, Team> teamsById =
                teams.stream()
                        .collect(
                                Collectors.toMap(
                                        Team::getTeamId,
                                        team -> team
                                )
                        );

        List<Long> unavailableTeamIds =
                requestedTeamIds.stream()
                        .filter(teamId -> {
                            Team team =
                                    teamsById.get(teamId);

                            return team == null
                                    || !Boolean.TRUE.equals(
                                            team.getActive()
                                    );
                        })
                        .toList();

        if (!unavailableTeamIds.isEmpty()) {
            throw new ResourceNotFoundException(
                    "Teams not found or inactive: "
                    + unavailableTeamIds
            );
        }
    }

    private List<User> loadActiveUsers(
            Collection<Long> userIds
    ) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }

        List<User> users =
                userRepository
                        .findAllByUserIdInAndActiveTrue(
                                userIds
                        );

        Map<Long, User> usersById =
                users.stream()
                        .collect(
                                Collectors.toMap(
                                        User::getUserId,
                                        user -> user,
                                        (first, second) -> first,
                                        LinkedHashMap::new
                                )
                        );

        List<Long> unavailableUserIds =
                userIds.stream()
                        .filter(userId ->
                                !usersById.containsKey(
                                        userId
                                )
                        )
                        .toList();

        if (!unavailableUserIds.isEmpty()) {
            throw new ResourceNotFoundException(
                    "Users not found or inactive: "
                    + unavailableUserIds
            );
        }

        /*
         * Preserve the order produced during individual and
         * team-member expansion.
         */
        return userIds.stream()
                .map(usersById::get)
                .toList();
    }

    private User getActiveUser(
            Long userId,
            String errorMessage
    ) {
        User user =
                userRepository.findById(userId)
                        .orElseThrow(
                                () ->
                                    new ResourceNotFoundException(
                                            errorMessage
                                    )
                        );

        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new ResourceNotFoundException(
                    errorMessage
            );
        }

        return user;
    }

    private CalendarEventParticipant
    createOrganizerParticipant(
            CalendarEvent event,
            User organizer,
            Instant now
    ) {
        CalendarEventParticipant participant =
                new CalendarEventParticipant();

        participant.setEvent(event);
        participant.setUser(organizer);
        participant.setInvitedBy(organizer);

        participant.setStatus(
                InvitationStatus.ACCEPTED
        );

        participant.setInvitedAt(now);
        participant.setRespondedAt(now);

        /*
         * expiresAt is mandatory in the database but has no
         * business effect for an already accepted organizer.
         */
        participant.setExpiresAt(
                now.plusSeconds(
                        INVITATION_VALIDITY_HOURS
                                * 60L
                                * 60L
                )
        );

        participant.setScheduleRevision(
                event.getScheduleRevision()
        );

        return participant;
    }

    private CalendarEventParticipant
    createPendingParticipant(
            CalendarEvent event,
            User invitedUser,
            User organizer,
            Instant invitedAt,
            Instant expiresAt
    ) {
        CalendarEventParticipant participant =
                new CalendarEventParticipant();

        participant.setEvent(event);
        participant.setUser(invitedUser);
        participant.setInvitedBy(organizer);

        participant.setStatus(
                InvitationStatus.PENDING
        );

        participant.setInvitedAt(invitedAt);
        participant.setExpiresAt(expiresAt);

        participant.setRespondedAt(null);
        participant.setRemovedAt(null);

        participant.setScheduleRevision(
                event.getScheduleRevision()
        );

        return participant;
    }

    private Instant calculateInvitationExpiry(
            Instant invitedAt,
            Instant eventStart
    ) {
        Instant fiveHoursLater =
                invitedAt.plusSeconds(
                        INVITATION_VALIDITY_HOURS
                                * 60L
                                * 60L
                );

        return eventStart.isBefore(fiveHoursLater)
                ? eventStart
                : fiveHoursLater;
    }

    private Instant getEventStartInstant(
            ValidatedCalendarSchedule schedule
    ) {
        if (!schedule.allDay()) {
            return schedule.startAt();
        }

        ZoneId zoneId =
                ZoneId.of(
                        schedule.timeZone()
                );

        return schedule.startDate()
                .atStartOfDay(zoneId)
                .toInstant();
    }

    private String normalizeRequiredText(
            String value,
            String errorMessage
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    errorMessage
            );
        }

        return value.trim();
    }

    private String normalizeOptionalText(
            String value
    ) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();

        return normalized.isEmpty()
                ? null
                : normalized;
    }

    @Override
public CalendarEventResponse getEvent(
        Long eventId
) {
    Long currentUserId =
            currentUserService.getCurrentUserId();

    CalendarEvent event =
            calendarEventRepository
                    .findByEventId(eventId)
                    .orElseThrow(
                            () ->
                                new ResourceNotFoundException(
                                        "Calendar event not found."
                                )
                    );

    CalendarEventParticipant currentParticipant =
            participantRepository
                    .findByEventEventIdAndUserUserId(
                            eventId,
                            currentUserId
                    )
                    .orElse(null);

    boolean organizer =
            event.getOrganizer()
                    .getUserId()
                    .equals(currentUserId);

    boolean visibleParticipant =
            currentParticipant != null
                    && currentParticipant.getStatus()
                    != InvitationStatus.REMOVED;

    /*
     * Return 404 instead of 403 so private event existence
     * is not disclosed to unrelated users.
     */
    if (!organizer && !visibleParticipant) {
        throw new ResourceNotFoundException(
                "Calendar event not found."
        );
    }

    long participantCount =
            participantRepository
                    .countByEventEventIdAndStatusNot(
                            eventId,
                            InvitationStatus.REMOVED
                    );

    return calendarEventMapper.toResponse(
            event,
            participantCount,
            currentUserId,
            currentParticipant
    );
  }

  @Override
public List<CalendarEventResponse> getEvents(
        Instant from,
        Instant to
) {
    validateCalendarRange(from, to);

    Long currentUserId =
            currentUserService.getCurrentUserId();

    ZoneId calendarZone =
            ZoneId.of("Asia/Kolkata");

    LocalDate fromDate =
            from.atZone(calendarZone)
                    .toLocalDate();

    LocalDate toDate =
            calculateExclusiveToDate(
                    to,
                    calendarZone
            );

    List<CalendarEvent> events =
            calendarEventRepository
                    .findVisibleEventsInRange(
                            currentUserId,
                            CalendarEventStatus.SCHEDULED,
                            from,
                            to,
                            fromDate,
                            toDate
                    );

    events.sort(
            Comparator.comparing(
                    this::getEffectiveStart
            )
    );

    return mapEventResponses(
            events,
            currentUserId
    );
}

@Override
public List<CalendarEventResponse> getUpcomingEvents(
        int limit
) {
    if (limit < 1 || limit > 50) {
        throw new IllegalArgumentException(
                "Upcoming event limit must be between 1 and 50."
        );
    }

    Long currentUserId =
            currentUserService.getCurrentUserId();

    Instant now = Instant.now();

    LocalDate today =
            now.atZone(
                    ZoneId.of("Asia/Kolkata")
            ).toLocalDate();

    /*
     * Load candidates from both schedule types, merge them,
     * sort by their effective start and then apply the limit.
     */
    int candidateLimit =
            Math.min(limit * 2, 100);

    Pageable candidates =
            PageRequest.of(
                    0,
                    candidateLimit
            );

    List<CalendarEvent> timedEvents =
            calendarEventRepository
                    .findUpcomingTimedEvents(
                            currentUserId,
                            CalendarEventStatus.SCHEDULED,
                            now,
                            candidates
                    )
                    .getContent();

    List<CalendarEvent> allDayEvents =
            calendarEventRepository
                    .findUpcomingAllDayEvents(
                            currentUserId,
                            CalendarEventStatus.SCHEDULED,
                            today,
                            candidates
                    )
                    .getContent();

    Map<Long, CalendarEvent> uniqueEvents =
            new LinkedHashMap<>();

    timedEvents.forEach(event ->
            uniqueEvents.put(
                    event.getEventId(),
                    event
            )
    );

    allDayEvents.forEach(event ->
            uniqueEvents.put(
                    event.getEventId(),
                    event
            )
    );

    List<CalendarEvent> events =
            uniqueEvents.values()
                    .stream()
                    .sorted(
                            Comparator.comparing(
                                    this::getEffectiveStart
                            )
                    )
                    .limit(limit)
                    .toList();

    return mapEventResponses(
            events,
            currentUserId
    );
}

private List<CalendarEventResponse> mapEventResponses(
        List<CalendarEvent> events,
        Long currentUserId
) {
    if (events.isEmpty()) {
        return List.of();
    }

    List<Long> eventIds =
            events.stream()
                    .map(CalendarEvent::getEventId)
                    .toList();

    Map<Long, CalendarEventParticipant>
            currentParticipantByEventId =
                participantRepository
                        .findAllByEventEventIdInAndUserUserId(
                                eventIds,
                                currentUserId
                        )
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        participant ->
                                            participant
                                                .getEvent()
                                                .getEventId(),
                                        Function.identity()
                                )
                        );

    Map<Long, Long> participantCountByEventId =
            participantRepository
                    .findParticipantCountsByEventIds(
                            eventIds
                    )
                    .stream()
                    .collect(
                            Collectors.toMap(
                                    EventParticipantCountProjection
                                            ::getEventId,
                                    projection ->
                                        projection
                                            .getParticipantCount()
                            )
                    );

    return events.stream()
            .map(event ->
                    calendarEventMapper.toResponse(
                            event,
                            participantCountByEventId
                                    .getOrDefault(
                                            event.getEventId(),
                                            0L
                                    ),
                            currentUserId,
                            currentParticipantByEventId
                                    .get(
                                        event.getEventId()
                                    )
                    )
            )
            .toList();
}

private void validateCalendarRange(
        Instant from,
        Instant to
) {
    if (from == null) {
        throw new IllegalArgumentException(
                "Calendar range start is required."
        );
    }

    if (to == null) {
        throw new IllegalArgumentException(
                "Calendar range end is required."
        );
    }

    if (!to.isAfter(from)) {
        throw new IllegalArgumentException(
                "Calendar range end must be after its start."
        );
    }

    if (Duration.between(from, to)
            .compareTo(Duration.ofDays(366)) > 0) {
        throw new IllegalArgumentException(
                "Calendar range cannot exceed 366 days."
        );
    }
}

private LocalDate calculateExclusiveToDate(
        Instant to,
        ZoneId zoneId
) {
    ZonedDateTime localTo =
            to.atZone(zoneId);

    /*
     * If the requested end is exactly midnight, that date
     * is already the exclusive end.
     *
     * If it contains a time, include that local date by using
     * the following date as the exclusive end.
     */
    if (localTo.toLocalTime()
            .equals(LocalTime.MIDNIGHT)) {
        return localTo.toLocalDate();
    }

    return localTo
            .toLocalDate()
            .plusDays(1);
}

private Instant getEffectiveStart(
        CalendarEvent event
) {
    if (!Boolean.TRUE.equals(
            event.getAllDay()
    )) {
        return event.getStartAt();
    }

    ZoneId zoneId =
            ZoneId.of(
                    event.getTimeZone()
            );

    return event.getStartDate()
            .atStartOfDay(zoneId)
            .toInstant();
}

@Override
@Transactional
public CalendarEventResponse updateEvent(
        Long eventId,
        UpdateCalendarEventRequest request
) {
    Long currentUserId =
            currentUserService.getCurrentUserId();

    CalendarEvent event =
            calendarEventRepository
                    .findByEventIdForUpdate(eventId)
                    .orElseThrow(
                            () ->
                                new ResourceNotFoundException(
                                    "Calendar event not found."
                                )
                    );

    validateEventCanBeEdited(
            event,
            currentUserId,
            request.getExpectedVersion()
    );

    if (!hasAnyEventUpdate(request)) {
        throw new IllegalArgumentException(
                "At least one event field must be provided."
        );
    }

    ValidatedCalendarSchedule updatedSchedule =
            scheduleValidator.validateForUpdate(
                    event,
                    request
            );

    boolean scheduleChanged =
            hasScheduleChanged(
                    event,
                    updatedSchedule
            );

    List<CalendarEventParticipant> participants =
            participantRepository
                    .findAllByEventEventIdAndStatusNot(
                            eventId,
                            InvitationStatus.REMOVED
                    );

    Instant now = Instant.now();

    if (scheduleChanged) {
        validateRenewedInvitationSchedule(
                event,
                participants,
                updatedSchedule,
                now
        );
    }

    applyEventMetadataUpdates(
            event,
            request
    );

    calendarEventMapper.applySchedule(
            event,
            updatedSchedule
    );

    if (scheduleChanged) {
        event.setScheduleRevision(
                event.getScheduleRevision() + 1
        );

        renewParticipants(
                event,
                participants,
                now
        );
    }

    event = calendarEventRepository.saveAndFlush(
        event
);

if (scheduleChanged) {
    participantRepository.saveAll(
            participants
    );

    participantRepository.flush();

    /*
     * These requests were created for the previous schedule
     * revision and can no longer be approved.
     */
    timeChangeRequestRepository
            .markPendingRequestsObsolete(
                    event.getEventId(),
                    TimeChangeRequestStatus.PENDING,
                    TimeChangeRequestStatus.OBSOLETE
            );
}

    CalendarEventParticipant organizerParticipant =
            participants.stream()
                    .filter(participant ->
                            participant
                                    .getUser()
                                    .getUserId()
                                    .equals(currentUserId)
                    )
                    .findFirst()
                    .orElse(null);

    long participantCount =
            participants.stream()
                    .filter(participant ->
                            participant.getStatus()
                                    != InvitationStatus.REMOVED
                    )
                    .count();

    return calendarEventMapper.toResponse(
            event,
            participantCount,
            currentUserId,
            organizerParticipant
    );
}

private void validateEventCanBeEdited(
        CalendarEvent event,
        Long currentUserId,
        Long expectedVersion
) {
    if (!event.getOrganizer()
            .getUserId()
            .equals(currentUserId)) {
        throw new ForbiddenOperationException(
                "Only the event organizer can edit this event."
        );
    }

    if (event.getStatus()
            == CalendarEventStatus.CANCELLED) {
        throw new ConflictException(
                "EVENT_ALREADY_CANCELLED",
                "A cancelled event cannot be edited."
        );
    }

    if (expectedVersion == null) {
        throw new IllegalArgumentException(
                "Expected version is required."
        );
    }

    if (!event.getVersion()
            .equals(expectedVersion)) {
        throw new ConflictException(
                "EVENT_VERSION_CONFLICT",
                "The event was updated by another request. "
                + "Refresh the event and try again."
        );
    }
}

private boolean hasAnyEventUpdate(
        UpdateCalendarEventRequest request
) {
    return request.isTitleProvided()
            || request.isDescriptionProvided()
            || request.isLocationProvided()
            || request.isMeetingUrlProvided()
            || request.isAllDayProvided()
            || request.isStartAtProvided()
            || request.isEndAtProvided()
            || request.isStartDateProvided()
            || request.isEndDateProvided()
            || request.isTimezoneProvided()
            || request.isVisibilityProvided()
            || request.isBlocksTimeProvided()
            || request.isCategoryIdProvided();
}


private boolean hasScheduleChanged(
        CalendarEvent event,
        ValidatedCalendarSchedule schedule
) {
    return Boolean.TRUE.equals(event.getAllDay())
                    != schedule.allDay()
            || !Objects.equals(
                    event.getStartAt(),
                    schedule.startAt()
            )
            || !Objects.equals(
                    event.getEndAt(),
                    schedule.endAt()
            )
            || !Objects.equals(
                    event.getStartDate(),
                    schedule.startDate()
            )
            || !Objects.equals(
                    event.getEndDate(),
                    schedule.endDate()
            )
            || !Objects.equals(
                    event.getTimeZone(),
                    schedule.timeZone()
            );
}

private void applyEventMetadataUpdates(
        CalendarEvent event,
        UpdateCalendarEventRequest request
) {
    if (request.isTitleProvided()) {
        event.setTitle(
                normalizeRequiredText(
                        request.getTitle(),
                        "Event title cannot be empty."
                )
        );
    }

    if (request.isCategoryIdProvided()) {
   event.setCategory(
        resolveEventCategory(
                request.getCategoryId()
        )
      );
    }

    if (request.isDescriptionProvided()) {
        event.setDescription(
                normalizeOptionalText(
                        request.getDescription()
                )
        );
    }

    if (request.isLocationProvided()) {
        event.setLocation(
                normalizeOptionalText(
                        request.getLocation()
                )
        );
    }

    if (request.isMeetingUrlProvided()) {
        event.setMeetingUrl(
                normalizeOptionalText(
                        request.getMeetingUrl()
                )
        );
    }

    if (request.isVisibilityProvided()) {
        if (request.getVisibility() == null) {
            throw new IllegalArgumentException(
                    "Visibility cannot be null."
            );
        }

        event.setVisibility(
                request.getVisibility()
        );
    }

    if (request.isBlocksTimeProvided()) {
        if (request.getBlocksTime() == null) {
            throw new IllegalArgumentException(
                    "blocksTime cannot be null."
            );
        }

        event.setBlocksTime(
                request.getBlocksTime()
        );
    }
}

private void validateRenewedInvitationSchedule(
        CalendarEvent event,
        List<CalendarEventParticipant> participants,
        ValidatedCalendarSchedule schedule,
        Instant now
) {
    boolean hasInvitedParticipants =
            participants.stream()
                    .anyMatch(participant ->
                            !participant
                                    .getUser()
                                    .getUserId()
                                    .equals(
                                        event.getOrganizer()
                                                .getUserId()
                                    )
                    );

    if (!hasInvitedParticipants) {
        return;
    }

    Instant eventStart =
            getEventStartInstant(schedule);

    if (!eventStart.isAfter(now)) {
        throw new IllegalArgumentException(
                "An event with participants cannot be "
                + "rescheduled to a time that has already started."
        );
    }
}

private void renewParticipants(
        CalendarEvent event,
        List<CalendarEventParticipant> participants,
        Instant now
) {
    Instant eventStart =
            getEventStartInstantFromEvent(
                    event
            );

    Instant invitationExpiry =
            calculateInvitationExpiry(
                    now,
                    eventStart
            );

    Long organizerId =
            event.getOrganizer()
                    .getUserId();

    for (CalendarEventParticipant participant
            : participants) {

        boolean organizer =
                participant
                        .getUser()
                        .getUserId()
                        .equals(organizerId);

        participant.setScheduleRevision(
                event.getScheduleRevision()
        );

        participant.setRemovedAt(null);

        if (organizer) {
            participant.setStatus(
                    InvitationStatus.ACCEPTED
            );

            participant.setRespondedAt(now);

            participant.setInvitedAt(now);

            participant.setExpiresAt(
                    now.plusSeconds(
                            INVITATION_VALIDITY_HOURS
                                    * 60L
                                    * 60L
                    )
            );

            continue;
        }

        participant.setStatus(
                InvitationStatus.PENDING
        );

        participant.setInvitedAt(now);
        participant.setExpiresAt(
                invitationExpiry
        );

        participant.setRespondedAt(null);
    }
}

private Instant getEventStartInstantFromEvent(
        CalendarEvent event
) {
    if (!Boolean.TRUE.equals(
            event.getAllDay()
    )) {
        return event.getStartAt();
    }

    ZoneId zoneId =
            ZoneId.of(
                    event.getTimeZone()
            );

    return event.getStartDate()
            .atStartOfDay(zoneId)
            .toInstant();
}

@Override
@Transactional
public void cancelEvent(
        Long eventId,
        Long expectedVersion
) {
    Long currentUserId =
            currentUserService.getCurrentUserId();

    CalendarEvent event =
            calendarEventRepository
                    .findByEventIdForUpdate(eventId)
                    .orElseThrow(
                            () ->
                                new ResourceNotFoundException(
                                        "Calendar event not found."
                                )
                    );

    validateOrganizer(
            event,
            currentUserId
    );

    validateEventVersion(
            event,
            expectedVersion
    );

    if (event.getStatus()
            == CalendarEventStatus.CANCELLED) {
        throw new ConflictException(
                "EVENT_ALREADY_CANCELLED",
                "The event is already cancelled."
        );
    }

    Instant now = Instant.now();

    event.setStatus(
            CalendarEventStatus.CANCELLED
    );

    event.setCancelledAt(now);

    List<CalendarEventParticipant> participants =
            participantRepository
                    .findAllByEventEventIdAndStatusNot(
                            eventId,
                            InvitationStatus.REMOVED
                    );

    /*
     * Pending invitations are no longer valid after the
     * event is cancelled.
     */
    participants.stream()
            .filter(participant ->
                    participant.getStatus()
                            == InvitationStatus.PENDING
            )
            .forEach(participant ->
                    participant.setStatus(
                            InvitationStatus.EXPIRED
                    )
            );

    calendarEventRepository.saveAndFlush(event);

    participantRepository.saveAll(participants);
    participantRepository.flush();
}

@Override
@Transactional
public ParticipantMutationResponse addParticipants(
        Long eventId,
        AddEventParticipantsRequest request
) {
    Long currentUserId =
            currentUserService.getCurrentUserId();

    CalendarEvent event =
            calendarEventRepository
                    .findByEventIdForUpdate(eventId)
                    .orElseThrow(
                            () ->
                                new ResourceNotFoundException(
                                        "Calendar event not found."
                                )
                    );

    validateEventParticipantManagement(
            event,
            currentUserId,
            request.expectedVersion()
    );

    if ((request.userIds() == null
            || request.userIds().isEmpty())
            && (request.teamIds() == null
            || request.teamIds().isEmpty())) {
        throw new IllegalArgumentException(
                "At least one user or team is required."
        );
    }

    Set<Long> directUserIds =
            request.userIds() == null
                    ? new LinkedHashSet<>()
                    : new LinkedHashSet<>(
                            request.userIds()
                    );

    Set<Long> teamUserIds =
            expandParticipantIds(
                    Set.of(),
                    request.teamIds()
            );

    Set<Long> duplicateUserIds =
            directUserIds.stream()
                    .filter(teamUserIds::contains)
                    .collect(
                            Collectors.toCollection(
                                    LinkedHashSet::new
                            )
                    );

    Set<Long> requestedUserIds =
            new LinkedHashSet<>();

    requestedUserIds.addAll(directUserIds);
    requestedUserIds.addAll(teamUserIds);

    List<Long> alreadyParticipatingUserIds =
            new ArrayList<>();

    /*
     * The organizer already participates and should never
     * receive an invitation.
     */
    if (requestedUserIds.remove(currentUserId)) {
        alreadyParticipatingUserIds.add(
                currentUserId
        );
    }

    List<User> requestedUsers =
            loadActiveUsers(
                    requestedUserIds
            );

    Map<Long, CalendarEventParticipant>
            existingParticipantsByUserId =
                participantRepository
                        .findAllByEventEventIdAndUserUserIdIn(
                                eventId,
                                requestedUserIds
                        )
                        .stream()
                        .collect(
                                Collectors.toMap(
                                    participant ->
                                        participant
                                            .getUser()
                                            .getUserId(),
                                    participant ->
                                        participant
                                )
                        );

    Instant now = Instant.now();

    Instant eventStart =
            getEventStartInstantFromEvent(
                    event
            );

    if (!requestedUsers.isEmpty()
            && !eventStart.isAfter(now)) {
        throw new ConflictException(
                "EVENT_ALREADY_STARTED",
                "Participants cannot be added after "
                + "the event has started."
        );
    }

    Instant expiresAt =
            calculateInvitationExpiry(
                    now,
                    eventStart
            );

    List<CalendarEventParticipant> changedParticipants =
            new ArrayList<>();

    for (User user : requestedUsers) {
        CalendarEventParticipant existing =
                existingParticipantsByUserId.get(
                        user.getUserId()
                );

        if (existing == null) {
            changedParticipants.add(
                    createPendingParticipant(
                            event,
                            user,
                            event.getOrganizer(),
                            now,
                            expiresAt
                    )
            );

            continue;
        }

        /*
         * Accepted and pending users are already active
         * participants and do not need another invitation.
         */
        if (existing.getStatus()
                == InvitationStatus.ACCEPTED
                || existing.getStatus()
                == InvitationStatus.PENDING) {
            alreadyParticipatingUserIds.add(
                    user.getUserId()
            );

            continue;
        }

        /*
         * A previously declined, expired or removed user can
         * be explicitly invited again.
         */
        existing.setStatus(
                InvitationStatus.PENDING
        );

        existing.setInvitedBy(
                event.getOrganizer()
        );

        existing.setInvitedAt(now);
        existing.setExpiresAt(expiresAt);

        existing.setRespondedAt(null);
        existing.setRemovedAt(null);

        existing.setScheduleRevision(
                event.getScheduleRevision()
        );

        changedParticipants.add(existing);
    }

    if (!changedParticipants.isEmpty()) {
        participantRepository.saveAll(
                changedParticipants
        );

        /*
         * Participant changes belong to the event aggregate,
         * so increment the event's optimistic-lock version.
         */
        event.setUpdatedAt(now);

        event = calendarEventRepository
                .saveAndFlush(event);

        participantRepository.flush();
    }

    List<CalendarEventParticipantResponse>
            addedResponses =
                changedParticipants.stream()
                        .map(participant ->
                                calendarEventMapper
                                        .toParticipantResponse(
                                                participant,
                                                currentUserId,
                                                now
                                        )
                        )
                        .toList();

    return new ParticipantMutationResponse(
            String.valueOf(event.getEventId()),
            event.getVersion(),
            addedResponses,
            alreadyParticipatingUserIds
                    .stream()
                    .map(String::valueOf)
                    .toList(),
            duplicateUserIds
                    .stream()
                    .map(String::valueOf)
                    .toList(),
            List.of()
    );
}

@Override
@Transactional
public void removeParticipant(
        Long eventId,
        Long userId,
        Long expectedVersion
) {
    Long currentUserId =
            currentUserService.getCurrentUserId();

    CalendarEvent event =
            calendarEventRepository
                    .findByEventIdForUpdate(eventId)
                    .orElseThrow(
                            () ->
                                new ResourceNotFoundException(
                                        "Calendar event not found."
                                )
                    );

    validateEventParticipantManagement(
            event,
            currentUserId,
            expectedVersion
    );

    if (event.getOrganizer()
            .getUserId()
            .equals(userId)) {
        throw new ConflictException(
                "EVENT_ORGANIZER_CANNOT_BE_REMOVED",
                "The event organizer cannot be removed."
        );
    }

    CalendarEventParticipant participant =
            participantRepository
                    .findByEventIdAndUserIdForUpdate(
                            eventId,
                            userId
                    )
                    .orElseThrow(
                            () ->
                                new ResourceNotFoundException(
                                    "Event participant not found."
                                )
                    );

    if (participant.getStatus()
            == InvitationStatus.REMOVED) {
        throw new ConflictException(
                "PARTICIPANT_ALREADY_REMOVED",
                "The participant has already been removed."
        );
    }

    Instant now = Instant.now();

    participant.setStatus(
            InvitationStatus.REMOVED
    );

    participant.setRemovedAt(now);

    participantRepository.saveAndFlush(
            participant
    );

    /*
     * Increment the event version because participant
     * membership changed.
     */
    event.setUpdatedAt(now);

    calendarEventRepository.saveAndFlush(
            event
    );
}

private void validateOrganizer(
        CalendarEvent event,
        Long currentUserId
) {
    if (!event.getOrganizer()
            .getUserId()
            .equals(currentUserId)) {
        throw new ForbiddenOperationException(
                "Only the event organizer can perform this operation."
        );
    }
}

private void validateEventVersion(
        CalendarEvent event,
        Long expectedVersion
) {
    if (expectedVersion == null) {
        throw new IllegalArgumentException(
                "Expected event version is required."
        );
    }

    if (!event.getVersion()
            .equals(expectedVersion)) {
        throw new ConflictException(
                "EVENT_VERSION_CONFLICT",
                "The event was updated by another request. "
                + "Refresh it and try again."
        );
    }
}

private void validateEventParticipantManagement(
        CalendarEvent event,
        Long currentUserId,
        Long expectedVersion
) {
    validateOrganizer(
            event,
            currentUserId
    );

    validateEventVersion(
            event,
            expectedVersion
    );

    if (event.getStatus()
            == CalendarEventStatus.CANCELLED) {
        throw new ConflictException(
                "EVENT_ALREADY_CANCELLED",
                "Participants cannot be changed on "
                + "a cancelled event."
        );
    }
}

@Override
public PageResponse<CalendarEventParticipantResponse>
getParticipants(
        Long eventId,
        int page,
        int size
) {
    validatePagination(page, size);

    Long currentUserId =
            currentUserService.getCurrentUserId();

    CalendarEvent event =
            calendarEventRepository
                    .findByEventId(eventId)
                    .orElseThrow(
                            () ->
                                new ResourceNotFoundException(
                                        "Calendar event not found."
                                )
                    );

    CalendarEventParticipant viewer =
            participantRepository
                    .findByEventEventIdAndUserUserId(
                            eventId,
                            currentUserId
                    )
                    .orElse(null);

    boolean organizer =
            event.getOrganizer()
                    .getUserId()
                    .equals(currentUserId);

    boolean activeParticipant =
            viewer != null
                    && viewer.getStatus()
                    != InvitationStatus.REMOVED;

    if (!organizer && !activeParticipant) {
        throw new ResourceNotFoundException(
                "Calendar event not found."
        );
    }

    Pageable pageable =
            PageRequest.of(
                    page,
                    size
            );

    org.springframework.data.domain.Page
            <CalendarEventParticipant> participantPage =
                participantRepository
                        .findActiveParticipants(
                                eventId,
                                pageable
                        );

    Instant now = Instant.now();

    List<CalendarEventParticipantResponse> responses =
            participantPage.getContent()
                    .stream()
                    .map(participant ->
                            calendarEventMapper
                                    .toParticipantResponse(
                                            participant,
                                            currentUserId,
                                            now
                                    )
                    )
                    .toList();

    return new PageResponse<>(
            responses,
            participantPage.getNumber(),
            participantPage.getSize(),
            participantPage.getTotalElements(),
            participantPage.getTotalPages(),
            participantPage.hasNext()
    );
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
}