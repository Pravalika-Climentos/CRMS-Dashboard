package com.example.Calendar.Service;

import com.example.CRM.Entity.Team;
import com.example.CRM.Entity.TeamMember;
import com.example.CRM.Entity.User;
import com.example.CRM.Repository.TeamMemberRepository;
import com.example.CRM.Repository.TeamRepository;
import com.example.CRM.Repository.UserRepository;
import com.example.Calendar.Availability.AvailabilityBlock;
import com.example.Calendar.Availability.AvailabilitySuggestionEngine;
import com.example.Calendar.Availability.CalendarAvailabilityData;
import com.example.Calendar.Availability.CalendarAvailabilityDataLoader;
import com.example.Calendar.DTO.Request.AvailabilityCheckRequest;
import com.example.Calendar.DTO.Request.AvailabilitySuggestionsRequest;
import com.example.Calendar.DTO.Request.ExactAvailabilityCheckRequest;
import com.example.Calendar.DTO.Response.*;
import com.example.Calendar.Entity.*;
import com.example.Calendar.Mapper.CalendarEventMapper;
import com.example.Calendar.Repository.CalendarEventParticipantRepository;
import com.example.Calendar.Repository.CalendarEventRepository;
import com.example.Common.DTO.Response.UserSummaryResponse;
import com.example.Common.Exception.ForbiddenOperationException;
import com.example.Common.Exception.ResourceNotFoundException;
import com.example.Common.Service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarAvailabilityServiceImpl
        implements CalendarAvailabilityService {

    private static final String DEFAULT_TIMEZONE =
            "Asia/Kolkata";
    private static final int DEFAULT_MAXIMUM_SLOTS_PER_DAY = 3;

    private static final int DEFAULT_WORKING_DAYS = 7;

    private static final LocalTime DEFAULT_WORK_START =
            LocalTime.of(9, 0);

    private static final LocalTime DEFAULT_WORK_END =
            LocalTime.of(18, 0);

    private static final int DEFAULT_DURATION_MINUTES = 30;

    private static final int MAX_USERS = 200;

    private static final int MAX_AVAILABLE_SLOTS = 50;

    private final CalendarEventRepository eventRepository;

    private final CalendarEventParticipantRepository
            participantRepository;

    private final UserRepository userRepository;

    private final TeamRepository teamRepository;

    private final TeamMemberRepository teamMemberRepository;

    private final CurrentUserService currentUserService;

    private final CalendarEventMapper calendarEventMapper;

    private final CalendarAvailabilityDataLoader availabilityDataLoader;

    private final AvailabilitySuggestionEngine availabilitySuggestionEngine;

    @Override
    public TeamCalendarResponse getTeamCalendar(
            Set<Long> userIds,
            Set<Long> teamIds,
            Integer workingDays,
            Instant from,
            Instant to
    ) {
        ZoneId zone =
                ZoneId.of(DEFAULT_TIMEZONE);

        Set<Long> expandedUserIds =
                expandUserIds(userIds, teamIds);

        List<User> users =
                loadActiveUsers(expandedUserIds);

        CalendarRange range =
                resolveRange(
                        workingDays,
                        from,
                        to,
                        zone
                );

        CalendarData calendarData =
                loadCalendarData(
                        expandedUserIds,
                        range,
                        zone
                );

        Long viewerId =
                currentUserService.getCurrentUserId();

        List<UserCalendarResponse> calendars =
                users.stream()
                        .sorted(
                                Comparator.comparing(
                                        User::getFullName,
                                        String.CASE_INSENSITIVE_ORDER
                                )
                        )
                        .map(user ->
                                new UserCalendarResponse(
                                        calendarEventMapper
                                                .toUserSummary(user),
                                        calendarData
                                                .eventsByUser()
                                                .getOrDefault(
                                                    user.getUserId(),
                                                    List.of()
                                                )
                                                .stream()
                                                .sorted(
                                                    Comparator.comparing(
                                                        this::effectiveStart
                                                    )
                                                )
                                                .map(event ->
                                                    toBusyBlock(
                                                        event,
                                                        viewerId
                                                    )
                                                )
                                                .toList()
                                )
                        )
                        .toList();

        return new TeamCalendarResponse(
                range.from(),
                range.to(),
                zone.getId(),
                range.workingDays(),
                calendars
        );
    }

    @Override
    public AvailabilityCheckResponse checkAvailability(
            AvailabilityCheckRequest request
    ) {
        ZoneId zone =
                parseZone(request.timezone());

        int workingDays =
                request.workingDays() == null
                        ? DEFAULT_WORKING_DAYS
                        : request.workingDays();

        validateWorkingDays(workingDays);

        int durationMinutes =
                request.durationMinutes() == null
                        ? DEFAULT_DURATION_MINUTES
                        : request.durationMinutes();

        if (durationMinutes < 15
                || durationMinutes > 480) {
            throw new IllegalArgumentException(
                    "Duration must be between 15 and 480 minutes."
            );
        }

        LocalTime workStart =
                request.workDayStart() == null
                        ? DEFAULT_WORK_START
                        : request.workDayStart();

        LocalTime workEnd =
                request.workDayEnd() == null
                        ? DEFAULT_WORK_END
                        : request.workDayEnd();

        if (!workEnd.isAfter(workStart)) {
            throw new IllegalArgumentException(
                    "workDayEnd must be after workDayStart."
            );
        }

        LocalDate startDate =
                request.fromDate() == null
                        ? LocalDate.now(zone)
                        : request.fromDate();

        Set<Long> expandedUserIds =
                expandUserIds(
                        request.userIds(),
                        request.teamIds()
                );

        List<User> users =
                loadActiveUsers(expandedUserIds);

        CalendarRange range =
                createWorkingDayRange(
                        startDate,
                        workingDays,
                        zone
                );

        CalendarData data =
                loadCalendarData(
                        expandedUserIds,
                        range,
                        zone
                );

        List<AvailableSlotResponse> availableSlots =
                calculateCommonAvailability(
                        data.eventsByUser(),
                        expandedUserIds,
                        startDate,
                        workingDays,
                        durationMinutes,
                        workStart,
                        workEnd,
                        zone
                );

        return new AvailabilityCheckResponse(
                range.from(),
                range.to(),
                zone.getId(),
                workingDays,
                durationMinutes,
                workStart,
                workEnd,
                users.stream()
                        .map(
                            calendarEventMapper
                                ::toUserSummary
                        )
                        .toList(),
                availableSlots
        );
    }

    @Override
public AvailabilitySuggestionsResponse
getAvailabilitySuggestions(
        AvailabilitySuggestionsRequest request
) {
    Objects.requireNonNull(
            request,
            "Availability suggestions request is required."
    );

    ZoneId zone =
            parseZone(request.timezone());

    int workingDays =
            request.workingDays() == null
                    ? DEFAULT_WORKING_DAYS
                    : request.workingDays();

    validateWorkingDays(workingDays);

    int durationMinutes =
            request.durationMinutes() == null
                    ? DEFAULT_DURATION_MINUTES
                    : request.durationMinutes();

    validateSuggestionDuration(durationMinutes);

    LocalTime workDayStart =
            request.workDayStart() == null
                    ? DEFAULT_WORK_START
                    : request.workDayStart();

    LocalTime workDayEnd =
            request.workDayEnd() == null
                    ? DEFAULT_WORK_END
                    : request.workDayEnd();

    validateSuggestionWorkday(
            workDayStart,
            workDayEnd,
            durationMinutes
    );

    int maximumSlotsPerDay =
            request.maximumSlotsPerDay() == null
                    ? DEFAULT_MAXIMUM_SLOTS_PER_DAY
                    : request.maximumSlotsPerDay();

    if (maximumSlotsPerDay < 1
            || maximumSlotsPerDay > 10) {
        throw new IllegalArgumentException(
                "maximumSlotsPerDay must be between 1 and 10."
        );
    }

    Instant now = Instant.now();

    LocalDate today =
            now.atZone(zone)
               .toLocalDate();

    LocalDate fromDate =
            request.fromDate() == null
                    ? today
                    : request.fromDate();

    /*
     * Never generate recommendations for previous dates.
     */
    if (fromDate.isBefore(today)) {
        fromDate = today;
    }

    Set<Long> expandedUserIds =
            expandUserIds(
                    request.userIds(),
                    request.teamIds()
            );

    /*
     * The organizer must also be available.
     *
     * At the moment CurrentUserService resolves the temporary
     * X-Dev-User-Id user.
     */
    Long currentUserId =
            currentUserService.getCurrentUserId();

    expandedUserIds.add(currentUserId);

    if (expandedUserIds.size() > MAX_USERS) {
        throw new IllegalArgumentException(
                "A maximum of 200 users can be checked."
        );
    }

    List<User> participants =
            loadActiveUsers(expandedUserIds);

    CalendarRange range =
            createWorkingDayRange(
                    fromDate,
                    workingDays,
                    zone
            );

    CalendarAvailabilityData availabilityData =
            availabilityDataLoader.load(
                    expandedUserIds,
                    range.from(),
                    range.to(),
                    zone,
                    now,
                    null
            );

    List<AvailabilityDayResponse> days =
            availabilitySuggestionEngine.recommend(
                    availabilityData,
                    expandedUserIds,
                    fromDate,
                    workingDays,
                    durationMinutes,
                    workDayStart,
                    workDayEnd,
                    maximumSlotsPerDay,
                    now
            );

    List<UserSummaryResponse> participantResponses =
            participants.stream()
                    .sorted(
                        Comparator.comparing(
                            User::getFullName,
                            String.CASE_INSENSITIVE_ORDER
                        )
                    )
                    .map(
                        calendarEventMapper::toUserSummary
                    )
                    .toList();

    return new AvailabilitySuggestionsResponse(
            range.from(),
            range.to(),
            zone.getId(),
            workingDays,
            durationMinutes,
            workDayStart,
            workDayEnd,
            maximumSlotsPerDay,
            participantResponses,
            days
    );
}

    @Override
public ExactAvailabilityCheckResponse checkExactAvailability(
        ExactAvailabilityCheckRequest request
) {
    Objects.requireNonNull(
            request,
            "Exact availability request is required."
    );

    ZoneId zone =
            parseZone(request.timezone());

    Instant startAt =
            request.startAt();

    Instant endAt =
            request.endAt();

    validateExactRange(
            startAt,
            endAt
    );

    Long currentUserId =
            currentUserService.getCurrentUserId();

    validateExcludedEvent(
            request.excludeEventId(),
            currentUserId
    );

    Set<Long> expandedUserIds =
            expandUserIds(
                    request.userIds(),
                    request.teamIds()
            );

    /*
     * The event organizer must also be checked.
     */
    expandedUserIds.add(currentUserId);

    if (expandedUserIds.size() > MAX_USERS) {
        throw new IllegalArgumentException(
                "A maximum of 200 users can be checked."
        );
    }

    List<User> users =
            loadActiveUsers(expandedUserIds);

    Instant now = Instant.now();

    CalendarAvailabilityData availabilityData =
            availabilityDataLoader.load(
                    expandedUserIds,
                    startAt,
                    endAt,
                    zone,
                    now,
                    request.excludeEventId()
            );

    List<ParticipantAvailabilityResponse>
            participantResponses =
                users.stream()
                    .sorted(
                        Comparator.comparing(
                            User::getFullName,
                            String.CASE_INSENSITIVE_ORDER
                        )
                    )
                    .map(user ->
                        toParticipantAvailability(
                                user,
                                availabilityData,
                                startAt,
                                endAt,
                                currentUserId
                        )
                    )
                    .toList();

    /*
     * According to the agreed calendar rule, conflicts
     * produce warnings but do not prevent event creation.
     *
     * Confirmed conflicts cause an overlapping invitation.
     * Tentative conflicts indicate an unconfirmed invitation.
     */
    boolean canCreate = true;

    return new ExactAvailabilityCheckResponse(
            startAt,
            endAt,
            zone.getId(),
            canCreate,
            participantResponses
    );
}

private ParticipantAvailabilityResponse
toParticipantAvailability(
        User user,
        CalendarAvailabilityData availabilityData,
        Instant requestedStart,
        Instant requestedEnd,
        Long viewerId
) {
    List<AvailabilityBlock> conflicts =
            availabilityData
                .blocksForUser(user.getUserId())
                .stream()
                .filter(block ->
                    overlaps(
                        requestedStart,
                        requestedEnd,
                        block.startAt(),
                        block.endAt()
                    )
                )
                .sorted(
                    Comparator
                        .comparing(
                            AvailabilityBlock::startAt
                        )
                        .thenComparing(
                            block ->
                                block.isBusy()
                                    ? 0
                                    : 1
                        )
                )
                .toList();

    ParticipantAvailability availability;

    if (conflicts.stream()
            .anyMatch(AvailabilityBlock::isBusy)) {
        availability =
                ParticipantAvailability.BUSY;

    } else if (conflicts.stream()
            .anyMatch(
                AvailabilityBlock::isTentative
            )) {
        availability =
                ParticipantAvailability.TENTATIVE;

    } else {
        availability =
                ParticipantAvailability.AVAILABLE;
    }

    List<AvailabilityConflictResponse>
            conflictResponses =
                conflicts.stream()
                    .map(block ->
                        toAvailabilityConflict(
                                block,
                                viewerId
                        )
                    )
                    .toList();

    return new ParticipantAvailabilityResponse(
            calendarEventMapper.toUserSummary(user),
            availability,
            conflictResponses
    );
}

private AvailabilityConflictResponse
toAvailabilityConflict(
        AvailabilityBlock block,
        Long viewerId
) {
    boolean privateEvent =
            block.isPrivateEvent();

    boolean canViewTitle =
            !privateEvent
            || Objects.equals(
                block.organizerId(),
                viewerId
            );

    AvailabilityConflictStatus status =
            block.isBusy()
                    ? AvailabilityConflictStatus.CONFIRMED
                    : AvailabilityConflictStatus.TENTATIVE;

    return new AvailabilityConflictResponse(
            String.valueOf(block.eventId()),

            canViewTitle
                    ? block.title()
                    : "Busy",

            privateEvent,
            status,
            block.allDay(),

            block.allDay()
                    ? null
                    : block.startAt(),

            block.allDay()
                    ? null
                    : block.endAt(),

            block.allDay()
                    ? block.startDate()
                    : null,

            block.allDay()
                    ? block.endDate()
                    : null,

            block.timeZone()
    );
}

private boolean overlaps(
        Instant firstStart,
        Instant firstEnd,
        Instant secondStart,
        Instant secondEnd
) {
    return firstStart.isBefore(secondEnd)
            && firstEnd.isAfter(secondStart);
}

private void validateExcludedEvent(
        Long excludeEventId,
        Long currentUserId
) {
    if (excludeEventId == null) {
        return;
    }

    CalendarEvent event =
            eventRepository
                .findByEventId(excludeEventId)
                .orElseThrow(() ->
                    new ResourceNotFoundException(
                        "Calendar event not found."
                    )
                );

    if (!Objects.equals(
            event.getOrganizer().getUserId(),
            currentUserId
    )) {
        throw new ForbiddenOperationException(
                "Only the event organizer can exclude this event."
        );
    }

    if (event.getStatus()
            == CalendarEventStatus.CANCELLED) {
        throw new IllegalArgumentException(
                "A cancelled event cannot be excluded."
        );
    }
}

private void validateExactRange(
        Instant startAt,
        Instant endAt
) {
    if (startAt == null || endAt == null) {
        throw new IllegalArgumentException(
                "startAt and endAt are required."
        );
    }

    if (!endAt.isAfter(startAt)) {
        throw new IllegalArgumentException(
                "endAt must be after startAt."
        );
    }

    long durationMinutes =
            Duration.between(
                    startAt,
                    endAt
            ).toMinutes();

    if (durationMinutes < 15
            || durationMinutes > 480) {
        throw new IllegalArgumentException(
                "Event duration must be between 15 and 480 minutes."
        );
    }

    if (durationMinutes % 15 != 0) {
        throw new IllegalArgumentException(
                "Event duration must use 15-minute increments."
        );
    }

    if (startAt.isBefore(Instant.now())) {
        throw new IllegalArgumentException(
                "Event start time cannot be in the past."
        );
    }
}

private void validateQuarterHourBoundary(
        Instant value,
        ZoneId zone,
        String fieldName
) {
    ZonedDateTime local =
            value.atZone(zone);

    if (local.getMinute() % 15 != 0
            || local.getSecond() != 0
            || local.getNano() != 0) {
        throw new IllegalArgumentException(
                fieldName
                + " must use a 15-minute boundary."
        );
    }
}

    private CalendarData loadCalendarData(
            Set<Long> userIds,
            CalendarRange range,
            ZoneId zone
    ) {
        List<Long> ids =
                List.copyOf(userIds);

        List<CalendarEvent> timed =
                eventRepository
                        .findBlockingTimedEventsForUsers(
                                ids,
                                CalendarEventStatus.SCHEDULED,
                                range.from(),
                                range.to()
                        );

        List<CalendarEvent> allDay =
                eventRepository
                        .findBlockingAllDayEventsForUsers(
                                ids,
                                CalendarEventStatus.SCHEDULED,
                                range.from()
                                        .atZone(zone)
                                        .toLocalDate(),
                                range.to()
                                        .atZone(zone)
                                        .toLocalDate()
                        );

        Map<Long, CalendarEvent> uniqueEvents =
                new LinkedHashMap<>();

        timed.forEach(event ->
                uniqueEvents.put(
                        event.getEventId(),
                        event
                )
        );

        allDay.forEach(event ->
                uniqueEvents.put(
                        event.getEventId(),
                        event
                )
        );

        Map<Long, List<CalendarEvent>> eventsByUser =
                new LinkedHashMap<>();

        userIds.forEach(userId ->
                eventsByUser.put(
                        userId,
                        new ArrayList<>()
                )
        );

        for (CalendarEvent event
                : uniqueEvents.values()) {
            Long organizerId =
                    event.getOrganizer()
                            .getUserId();

            if (userIds.contains(organizerId)) {
                eventsByUser
                        .get(organizerId)
                        .add(event);
            }
        }

        if (!uniqueEvents.isEmpty()) {
            List<CalendarEventParticipant> participants =
                    participantRepository
                            .findAllByEventEventIdInAndUserUserIdInAndStatus(
                                    uniqueEvents.keySet(),
                                    userIds,
                                    InvitationStatus.ACCEPTED
                            );

            for (CalendarEventParticipant participant
                    : participants) {
                Long userId =
                        participant
                                .getUser()
                                .getUserId();

                List<CalendarEvent> userEvents =
                        eventsByUser.get(userId);

                if (userEvents != null
                        && userEvents.stream()
                            .noneMatch(event ->
                                event.getEventId().equals(
                                    participant
                                        .getEvent()
                                        .getEventId()
                                )
                            )) {
                    userEvents.add(
                            participant.getEvent()
                    );
                }
            }
        }

        return new CalendarData(
                uniqueEvents,
                eventsByUser
        );
    }

    private CalendarBusyBlockResponse toBusyBlock(
            CalendarEvent event,
            Long viewerId
    ) {
        boolean privateEvent =
                event.getVisibility()
                        == CalendarVisibility.PRIVATE;

        boolean canViewDetails =
                !privateEvent
                || event.getOrganizer()
                        .getUserId()
                        .equals(viewerId);

        return new CalendarBusyBlockResponse(
                String.valueOf(event.getEventId()),
                canViewDetails
                        ? event.getTitle()
                        : "Busy",
                canViewDetails
                        ? event.getLocation()
                        : null,
                privateEvent && !canViewDetails,
                Boolean.TRUE.equals(event.getAllDay()),
                event.getStartAt(),
                event.getEndAt(),
                event.getStartDate(),
                event.getEndDate(),
                event.getTimeZone()
        );
    }

    private List<AvailableSlotResponse>
    calculateCommonAvailability(
            Map<Long, List<CalendarEvent>> eventsByUser,
            Set<Long> userIds,
            LocalDate startingDate,
            int workingDays,
            int durationMinutes,
            LocalTime workStart,
            LocalTime workEnd,
            ZoneId zone
    ) {
        List<AvailableSlotResponse> results =
                new ArrayList<>();

        LocalDate date = startingDate;
        int processedWorkingDays = 0;

        while (processedWorkingDays < workingDays
                && results.size()
                    < MAX_AVAILABLE_SLOTS) {

            if (isWorkingDay(date)) {
                processedWorkingDays++;

                Instant dayStart =
                        date.atTime(workStart)
                                .atZone(zone)
                                .toInstant();

                Instant dayEnd =
                        date.atTime(workEnd)
                                .atZone(zone)
                                .toInstant();

                List<BusyInterval> busyIntervals =
                        new ArrayList<>();

                for (Long userId : userIds) {
                    for (CalendarEvent event
                            : eventsByUser.getOrDefault(
                                userId,
                                List.of()
                            )) {
                        BusyInterval interval =
                                toBusyInterval(
                                        event,
                                        dayStart,
                                        dayEnd,
                                        zone
                                );

                        if (interval != null) {
                            busyIntervals.add(interval);
                        }
                    }
                }

                List<BusyInterval> merged =
                        mergeIntervals(
                                busyIntervals
                        );

                Instant cursor = dayStart;

                for (BusyInterval busy : merged) {
                    if (Duration.between(
                            cursor,
                            busy.start()
                        ).toMinutes() >= durationMinutes) {
                        addAvailableSlots(
                                results,
                                date,
                                cursor,
                                busy.start(),
                                durationMinutes
                        );
                    }

                    if (busy.end().isAfter(cursor)) {
                        cursor = busy.end();
                    }
                }

                if (Duration.between(
                        cursor,
                        dayEnd
                ).toMinutes() >= durationMinutes) {
                    addAvailableSlots(
                            results,
                            date,
                            cursor,
                            dayEnd,
                            durationMinutes
                    );
                }
            }

            date = date.plusDays(1);
        }

        return results.stream()
                .limit(MAX_AVAILABLE_SLOTS)
                .toList();
    }

    private BusyInterval toBusyInterval(
            CalendarEvent event,
            Instant dayStart,
            Instant dayEnd,
            ZoneId zone
    ) {
        Instant eventStart;
        Instant eventEnd;

        if (Boolean.TRUE.equals(event.getAllDay())) {
            eventStart =
                    event.getStartDate()
                            .atStartOfDay(zone)
                            .toInstant();

            eventEnd =
                    event.getEndDate()
                            .atStartOfDay(zone)
                            .toInstant();
        } else {
            eventStart = event.getStartAt();
            eventEnd = event.getEndAt();
        }

        Instant clippedStart =
                eventStart.isAfter(dayStart)
                        ? eventStart
                        : dayStart;

        Instant clippedEnd =
                eventEnd.isBefore(dayEnd)
                        ? eventEnd
                        : dayEnd;

        if (!clippedEnd.isAfter(clippedStart)) {
            return null;
        }

        return new BusyInterval(
                clippedStart,
                clippedEnd
        );
    }

    private List<BusyInterval> mergeIntervals(
            List<BusyInterval> intervals
    ) {
        if (intervals.isEmpty()) {
            return List.of();
        }

        List<BusyInterval> sorted =
                intervals.stream()
                        .sorted(
                            Comparator.comparing(
                                BusyInterval::start
                            )
                        )
                        .toList();

        List<BusyInterval> merged =
                new ArrayList<>();

        BusyInterval current = sorted.get(0);

        for (int index = 1;
             index < sorted.size();
             index++) {

            BusyInterval next =
                    sorted.get(index);

            if (!next.start()
                    .isAfter(current.end())) {
                current =
                        new BusyInterval(
                                current.start(),
                                next.end().isAfter(
                                        current.end()
                                )
                                    ? next.end()
                                    : current.end()
                        );
            } else {
                merged.add(current);
                current = next;
            }
        }

        merged.add(current);

        return merged;
    }

    private void addAvailableSlots(
            List<AvailableSlotResponse> results,
            LocalDate date,
            Instant availableStart,
            Instant availableEnd,
            int durationMinutes
    ) {
        Instant slotStart = availableStart;

        while (!slotStart
                .plusSeconds(
                    durationMinutes * 60L
                )
                .isAfter(availableEnd)
                && results.size()
                    < MAX_AVAILABLE_SLOTS) {

            Instant slotEnd =
                    slotStart.plusSeconds(
                            durationMinutes * 60L
                    );

            results.add(
                    new AvailableSlotResponse(
                            date,
                            slotStart,
                            slotEnd,
                            durationMinutes
                    )
            );

            slotStart = slotEnd;
        }
    }

    private Set<Long> expandUserIds(
            Set<Long> requestedUserIds,
            Set<Long> teamIds
    ) {
        Set<Long> userIds =
                requestedUserIds == null
                        ? new LinkedHashSet<>()
                        : new LinkedHashSet<>(
                            requestedUserIds
                        );
        

        if (teamIds.stream().anyMatch(
        teamId -> teamId == null || teamId <= 0
         )) {
              throw new IllegalArgumentException(
            "Team IDs must be positive."
              );
          }

        if (teamIds != null
                && !teamIds.isEmpty()) {
            List<Team> teams =
                    teamRepository.findAllById(
                            teamIds
                    );

            Set<Long> activeTeamIds =
                    teams.stream()
                            .filter(team ->
                                Boolean.TRUE.equals(
                                    team.getActive()
                                )
                            )
                            .map(Team::getTeamId)
                            .collect(
                                Collectors.toSet()
                            );

            if (activeTeamIds.size()
                    != new HashSet<>(teamIds).size()) {
                throw new ResourceNotFoundException(
                        "One or more teams were not found or are inactive."
                );
            }

            teamMemberRepository
                    .findAllWithUsersByTeamIds(
                            activeTeamIds
                    )
                    .stream()
                    .map(TeamMember::getUser)
                    .filter(user ->
                        Boolean.TRUE.equals(
                            user.getActive()
                        )
                    )
                    .map(User::getUserId)
                    .forEach(userIds::add);
        }

        if (userIds.stream().anyMatch(
        userId -> userId == null || userId <= 0
            )) {
                  throw new IllegalArgumentException(
                  "User IDs must be positive."
                  );
                }

        if (userIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one user or team is required."
            );
        }

        if (userIds.size() > MAX_USERS) {
            throw new IllegalArgumentException(
                    "A maximum of 200 users can be checked."
            );
        }

        return userIds;
    }

    private List<User> loadActiveUsers(
            Set<Long> userIds
    ) {
        List<User> users =
                userRepository
                        .findAllByUserIdInAndActiveTrue(
                                userIds
                        );

        if (users.size() != userIds.size()) {
            throw new ResourceNotFoundException(
                    "One or more users were not found or are inactive."
            );
        }

        return users;
    }

    private CalendarRange resolveRange(
            Integer workingDays,
            Instant from,
            Instant to,
            ZoneId zone
    ) {
        if ((from == null) != (to == null)) {
            throw new IllegalArgumentException(
                    "from and to must be supplied together."
            );
        }

        if (from != null) {
            if (!to.isAfter(from)) {
                throw new IllegalArgumentException(
                        "to must be after from."
                );
            }

            if (Duration.between(from, to)
                    .compareTo(
                        Duration.ofDays(62)
                    ) > 0) {
                throw new IllegalArgumentException(
                        "Team-calendar range cannot exceed 62 days."
                );
            }

            return new CalendarRange(
                    from,
                    to,
                    countWorkingDays(
                            from.atZone(zone)
                                    .toLocalDate(),
                            to.atZone(zone)
                                    .toLocalDate()
                    )
            );
        }

        int days =
                workingDays == null
                        ? DEFAULT_WORKING_DAYS
                        : workingDays;

        validateWorkingDays(days);

        return createWorkingDayRange(
                LocalDate.now(zone),
                days,
                zone
        );
    }

    private CalendarRange createWorkingDayRange(
            LocalDate startDate,
            int workingDays,
            ZoneId zone
    ) {
        LocalDate cursor = startDate;
        int included = 0;

        while (included < workingDays) {
            if (isWorkingDay(cursor)) {
                included++;
            }

            cursor = cursor.plusDays(1);
        }

        return new CalendarRange(
                startDate.atStartOfDay(zone)
                        .toInstant(),
                cursor.atStartOfDay(zone)
                        .toInstant(),
                workingDays
        );
    }

    private int countWorkingDays(
            LocalDate from,
            LocalDate to
    ) {
        int count = 0;
        LocalDate date = from;

        while (date.isBefore(to)) {
            if (isWorkingDay(date)) {
                count++;
            }

            date = date.plusDays(1);
        }

        return count;
    }

    private boolean isWorkingDay(
            LocalDate date
    ) {
        return date.getDayOfWeek()
                        != DayOfWeek.SATURDAY
                && date.getDayOfWeek()
                        != DayOfWeek.SUNDAY;
    }

    private void validateWorkingDays(
            int workingDays
    ) {
        if (workingDays < 1
                || workingDays > 31) {
            throw new IllegalArgumentException(
                    "Working days must be between 1 and 31."
            );
        }
    }

    private ZoneId parseZone(
            String timezone
    ) {
        try {
            return ZoneId.of(
                    timezone == null
                            || timezone.isBlank()
                            ? DEFAULT_TIMEZONE
                            : timezone.trim()
            );
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException(
                    "Invalid timezone."
            );
        }
    }

    private Instant effectiveStart(
            CalendarEvent event
    ) {
        if (!Boolean.TRUE.equals(
                event.getAllDay()
        )) {
            return event.getStartAt();
        }

        return event.getStartDate()
                .atStartOfDay(
                    ZoneId.of(
                        event.getTimeZone()
                    )
                )
                .toInstant();
    }

    private record CalendarRange(
            Instant from,
            Instant to,
            int workingDays
    ) {
    }

    private record CalendarData(
            Map<Long, CalendarEvent> events,
            Map<Long, List<CalendarEvent>> eventsByUser
    ) {
    }

    private record BusyInterval(
            Instant start,
            Instant end
    ) {
    }

    private void validateSuggestionDuration(
        int durationMinutes
) {
    if (durationMinutes < 15
            || durationMinutes > 480) {
        throw new IllegalArgumentException(
                "Duration must be between 15 and 480 minutes."
        );
    }

    if (durationMinutes % 15 != 0) {
        throw new IllegalArgumentException(
                "Duration must use 15-minute increments."
        );
    }
}

private void validateSuggestionWorkday(
        LocalTime workDayStart,
        LocalTime workDayEnd,
        int durationMinutes
) {
    if (!workDayStart.isBefore(workDayEnd)) {
        throw new IllegalArgumentException(
                "workDayStart must be before workDayEnd."
        );
    }

    long workDayMinutes =
            Duration.between(
                    workDayStart,
                    workDayEnd
            ).toMinutes();

    if (durationMinutes > workDayMinutes) {
        throw new IllegalArgumentException(
                "Duration cannot exceed the configured workday."
        );
    }
}
}