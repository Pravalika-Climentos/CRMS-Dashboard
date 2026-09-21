package com.example.Calendar.Service;

import com.example.Calendar.DTO.Request.CreateTimeChangeRequest;
import com.example.Calendar.DTO.Request.DecideTimeChangeRequest;
import com.example.Calendar.DTO.Request.TimeChangeDecision;
import com.example.Calendar.DTO.Response.CalendarTimeChangeResponse;
import com.example.Calendar.Entity.*;
import com.example.Calendar.Mapper.CalendarEventMapper;
import com.example.Calendar.Mapper.CalendarTimeChangeMapper;
import com.example.Calendar.Repository.CalendarEventParticipantRepository;
import com.example.Calendar.Repository.CalendarEventRepository;
import com.example.Calendar.Repository.CalendarTimeChangeRequestRepository;
import com.example.Calendar.Validation.CalendarScheduleValidator;
import com.example.Calendar.Validation.ValidatedCalendarSchedule;
import com.example.Common.DTO.Response.PageResponse;
import com.example.Common.Exception.ConflictException;
import com.example.Common.Exception.ForbiddenOperationException;
import com.example.Common.Exception.ResourceNotFoundException;
import com.example.Common.Service.CurrentUserService;
import com.example.Email.Service.InternalEmailNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarTimeChangeServiceImpl
        implements CalendarTimeChangeService {

    private static final long INVITATION_VALIDITY_HOURS = 5L;

    private final CalendarTimeChangeRequestRepository
            requestRepository;

    private final CalendarEventRepository eventRepository;

    private final CalendarEventParticipantRepository
            participantRepository;

    private final CalendarScheduleValidator scheduleValidator;

    private final CalendarTimeChangeMapper timeChangeMapper;

    private final CalendarEventMapper eventMapper;

    private final CurrentUserService currentUserService;

    private final InternalEmailNotificationService emailNotifications;

  

    @Override
    @Transactional
    public CalendarTimeChangeResponse create(
            Long eventId,
            CreateTimeChangeRequest request
    ) {
        Long currentUserId =
                currentUserService.getCurrentUserId();

        CalendarEvent event =
                eventRepository.findByEventId(eventId)
                        .orElseThrow(
                            () ->
                                new ResourceNotFoundException(
                                    "Calendar event not found."
                                )
                        );

        if (event.getOrganizer()
                .getUserId()
                .equals(currentUserId)) {
            throw new ForbiddenOperationException(
                    "The organizer cannot submit a "
                    + "time-change request for their own event."
            );
        }

        if (event.getStatus()
                != CalendarEventStatus.SCHEDULED) {
            throw new ConflictException(
                    "EVENT_NOT_ACTIVE",
                    "The event is not active."
            );
        }

        CalendarEventParticipant participant =
                participantRepository
                        .findByEventEventIdAndUserUserId(
                                eventId,
                                currentUserId
                        )
                        .orElseThrow(
                            () ->
                                new ForbiddenOperationException(
                                    "Only an event participant can "
                                    + "request a time change."
                                )
                        );

       Instant now = Instant.now();

       validateTimeChangeRequester(participant,event,now);         

        if (!event.getScheduleRevision()
                .equals(
                    request.expectedScheduleRevision()
                )) {
            throw new ConflictException(
                    "EVENT_SCHEDULE_CHANGED",
                    "The event schedule has changed. "
                    + "Refresh the event and try again."
            );
        }

        requestRepository
                .findByEventEventIdAndRequesterUserIdAndScheduleRevisionAndStatus(
                        eventId,
                        currentUserId,
                        event.getScheduleRevision(),
                        TimeChangeRequestStatus.PENDING
                )
                .ifPresent(existing -> {
                    throw new ConflictException(
                            "TIME_CHANGE_REQUEST_ALREADY_PENDING",
                            "You already have a pending request "
                            + "for this event schedule."
                    );
                });

        ValidatedCalendarSchedule proposal =
                scheduleValidator.validateProposal(
                        request.proposedAllDay(),
                        request.proposedStartAt(),
                        request.proposedEndAt(),
                        request.proposedStartDate(),
                        request.proposedEndDate(),
                        request.proposedTimezone()
                );

        if (!isDifferentSchedule(
                event,
                proposal
        )) {
            throw new IllegalArgumentException(
                    "The proposed schedule is the same "
                    + "as the current event schedule."
            );
        }

        CalendarTimeChangeRequest entity =
                new CalendarTimeChangeRequest();

        entity.setEvent(event);
        entity.setRequester(
                participant.getUser()
        );

        entity.setScheduleRevision(
                event.getScheduleRevision()
        );

        entity.setProposedAllDay(
                proposal.allDay()
        );

        entity.setProposedStartAt(
                proposal.startAt()
        );

        entity.setProposedEndAt(
                proposal.endAt()
        );

        entity.setProposedStartDate(
                proposal.startDate()
        );

        entity.setProposedEndDate(
                proposal.endDate()
        );

        entity.setProposedTimeZone(
                proposal.timeZone()
        );

        entity.setReason(
                request.reason().trim()
        );

        entity.setStatus(
                TimeChangeRequestStatus.PENDING
        );

        entity = requestRepository.saveAndFlush(
                entity
        );

        emailNotifications.send(
                entity.getRequester(),
                List.of(event.getOrganizer()),
                "Time-change request: " + event.getTitle(),
                entity.getRequester().getFullName()
                        + " requested a different time for “" + event.getTitle() + "”.\n\n"
                        + "Reason: " + entity.getReason() + "\n\n"
                        + "Open Calendar in CRMS to review the proposed time."
        );

        return timeChangeMapper.toResponse(
                entity,
                currentUserId
        );
    }

    @Override
    public PageResponse<CalendarTimeChangeResponse>
    getRequests(
            String scope,
            String status,
            int page,
            int size
    ) {
        validatePagination(page, size);

        Long currentUserId =
                currentUserService.getCurrentUserId();

        String normalizedScope =
                scope == null
                        ? "received"
                        : scope.trim().toLowerCase();

        String normalizedStatus =
                status == null
                        ? "ALL"
                        : status.trim().toUpperCase();

        TimeChangeRequestStatus parsedStatus =
                parseStatus(normalizedStatus);

        Pageable pageable =
                PageRequest.of(
                        page,
                        size,
                        Sort.by(
                            Sort.Direction.DESC,
                            "createdAt"
                        )
                );

        Page<CalendarTimeChangeRequest> result;

        switch (normalizedScope) {
            case "sent" -> {
                result =
                    parsedStatus == null
                        ? requestRepository
                            .findByRequesterUserId(
                                currentUserId,
                                pageable
                            )
                        : requestRepository
                            .findByRequesterUserIdAndStatus(
                                currentUserId,
                                parsedStatus,
                                pageable
                            );
            }

            case "received" -> {
                result =
                    parsedStatus == null
                        ? requestRepository
                            .findReceivedByOrganizer(
                                currentUserId,
                                pageable
                            )
                        : requestRepository
                            .findReceivedByOrganizerAndStatus(
                                currentUserId,
                                parsedStatus,
                                pageable
                            );
            }

            default ->
                throw new IllegalArgumentException(
                        "Scope must be sent or received."
                );
        }

        List<CalendarTimeChangeResponse> responses =
                result.getContent()
                        .stream()
                        .map(entity ->
                            timeChangeMapper.toResponse(
                                entity,
                                currentUserId
                            )
                        )
                        .toList();

        return new PageResponse<>(
                responses,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }

    @Override
    public CalendarTimeChangeResponse getRequest(
            Long requestId
    ) {
        Long currentUserId =
                currentUserService.getCurrentUserId();

        CalendarTimeChangeRequest request =
                requestRepository.findById(requestId)
                        .orElseThrow(
                            () ->
                                new ResourceNotFoundException(
                                    "Time-change request not found."
                                )
                        );

        validateRequestVisibility(
                request,
                currentUserId
        );

        return timeChangeMapper.toResponse(
                request,
                currentUserId
        );
    }

    @Override
    @Transactional
    public CalendarTimeChangeResponse decide(
            Long requestId,
            DecideTimeChangeRequest request
    ) {
        Long currentUserId =
                currentUserService.getCurrentUserId();

        /*
         * Read the request first to obtain its event ID.
         * Then lock the event before locking the request.
         */
        CalendarTimeChangeRequest initial =
                requestRepository.findById(requestId)
                        .orElseThrow(
                            () ->
                                new ResourceNotFoundException(
                                    "Time-change request not found."
                                )
                        );

        Long eventId =
                initial.getEvent().getEventId();

        CalendarEvent event =
                eventRepository
                        .findByEventIdForUpdate(eventId)
                        .orElseThrow(
                            () ->
                                new ResourceNotFoundException(
                                    "Calendar event not found."
                                )
                        );

        CalendarTimeChangeRequest entity =
                requestRepository
                        .findByIdForUpdate(requestId)
                        .orElseThrow(
                            () ->
                                new ResourceNotFoundException(
                                    "Time-change request not found."
                                )
                        );

        if (!event.getOrganizer()
                .getUserId()
                .equals(currentUserId)) {
            throw new ForbiddenOperationException(
                    "Only the event organizer can decide "
                    + "this request."
            );
        }

        validatePendingRequest(
                entity,
                request.expectedRequestVersion()
        );

        if (!event.getVersion()
                .equals(request.expectedEventVersion())) {
            throw new ConflictException(
                    "EVENT_VERSION_CONFLICT",
                    "The event was changed. Refresh and try again."
            );
        }

        if (!entity.getScheduleRevision()
                .equals(event.getScheduleRevision())) {
            throw new ConflictException(
                    "TIME_CHANGE_REQUEST_OBSOLETE",
                    "This request refers to an old event schedule."
            );
        }

        if (event.getStatus()
                != CalendarEventStatus.SCHEDULED) {
            throw new ConflictException(
                    "EVENT_NOT_ACTIVE",
                    "The event is no longer active."
            );
        }

        Instant now = Instant.now();

        if (request.decision()
                == TimeChangeDecision.REJECTED) {
            entity.setStatus(
                    TimeChangeRequestStatus.REJECTED
            );

            entity.setDecidedBy(
                    event.getOrganizer()
            );

            entity.setDecisionNote(
                    normalizeOptional(
                        request.decisionNote()
                    )
            );

            entity.setDecidedAt(now);

            entity = requestRepository
                    .saveAndFlush(entity);

            emailNotifications.send(
                    event.getOrganizer(),
                    List.of(entity.getRequester()),
                    "Time-change request declined: " + event.getTitle(),
                    "Your request to change the time for “" + event.getTitle()
                            + "” was declined."
                            + decisionNoteText(entity.getDecisionNote())
                            + "\n\nOpen Calendar in CRMS for event details."
            );

            return timeChangeMapper.toResponse(
                    entity,
                    currentUserId
            );
        }

        ValidatedCalendarSchedule proposal =
                scheduleValidator.validateProposal(
                        entity.getProposedAllDay(),
                        entity.getProposedStartAt(),
                        entity.getProposedEndAt(),
                        entity.getProposedStartDate(),
                        entity.getProposedEndDate(),
                        entity.getProposedTimeZone()
                );

        List<CalendarEventParticipant> participants =
                participantRepository
                        .findAllByEventEventIdAndStatusNot(
                            eventId,
                            InvitationStatus.REMOVED
                        );

        Instant proposedStart =
                scheduleStart(proposal);

        boolean hasOtherParticipants =
                participants.stream()
                        .anyMatch(participant ->
                            !participant.getUser()
                                .getUserId()
                                .equals(
                                    event.getOrganizer()
                                        .getUserId()
                                )
                        );

        if (hasOtherParticipants
                && !proposedStart.isAfter(now)) {
            throw new ConflictException(
                    "PROPOSED_TIME_ALREADY_STARTED",
                    "The proposed time has already started."
            );
        }

        eventMapper.applySchedule(
                event,
                proposal
        );

        event.setScheduleRevision(
                event.getScheduleRevision() + 1
        );

        renewInvitations(
                event,
                participants,
                now
        );

        eventRepository.saveAndFlush(
                event
        );

        participantRepository.saveAll(
                participants
        );

        entity.setStatus(
                TimeChangeRequestStatus.APPROVED
        );

        entity.setDecidedBy(
                event.getOrganizer()
        );

        entity.setDecisionNote(
                normalizeOptional(
                    request.decisionNote()
                )
        );

        entity.setDecidedAt(now);

        entity = requestRepository
                .saveAndFlush(entity);

        requestRepository
                .markOtherPendingRequestsObsolete(
                        eventId,
                        entity.getRequestId(),
                        TimeChangeRequestStatus.PENDING,
                        TimeChangeRequestStatus.OBSOLETE,
                        now
                );

        emailNotifications.send(
                event.getOrganizer(),
                participants.stream()
                        .map(CalendarEventParticipant::getUser)
                        .toList(),
                "Meeting time updated: " + event.getTitle(),
                "The time-change request for “" + event.getTitle()
                        + "” was approved and the meeting schedule was updated."
                        + decisionNoteText(entity.getDecisionNote())
                        + "\n\nOpen Calendar in CRMS to review the new time and respond."
        );

        return timeChangeMapper.toResponse(
                entity,
                currentUserId
        );
    }

    @Override
    @Transactional
    public void withdraw(
            Long requestId,
            Long expectedVersion
    ) {
        Long currentUserId =
                currentUserService.getCurrentUserId();

        CalendarTimeChangeRequest request =
                requestRepository
                        .findByIdForUpdate(requestId)
                        .orElseThrow(
                            () ->
                                new ResourceNotFoundException(
                                    "Time-change request not found."
                                )
                        );

        if (!request.getRequester()
                .getUserId()
                .equals(currentUserId)) {
            throw new ForbiddenOperationException(
                    "Only the requester can withdraw this request."
            );
        }

        validatePendingRequest(
                request,
                expectedVersion
        );

        request.setStatus(
                TimeChangeRequestStatus.WITHDRAWN
        );

        requestRepository.saveAndFlush(
                request
        );

        emailNotifications.send(
                request.getRequester(),
                List.of(request.getEvent().getOrganizer()),
                "Time-change request withdrawn: " + request.getEvent().getTitle(),
                request.getRequester().getFullName() + " withdrew the time-change request for “"
                        + request.getEvent().getTitle() + "”."
        );
    }

    private String decisionNoteText(String decisionNote) {
        return decisionNote == null || decisionNote.isBlank()
                ? ""
                : "\n\nOrganizer note: " + decisionNote;
    }

    private void renewInvitations(
            CalendarEvent event,
            List<CalendarEventParticipant> participants,
            Instant now
    ) {
        Instant eventStart =
                eventStart(event);

        Instant expiresAt =
                eventStart.isBefore(
                    now.plusSeconds(
                        INVITATION_VALIDITY_HOURS * 3600L
                    )
                )
                ? eventStart
                : now.plusSeconds(
                    INVITATION_VALIDITY_HOURS * 3600L
                );

        Long organizerId =
                event.getOrganizer().getUserId();

        for (CalendarEventParticipant participant
                : participants) {
            participant.setScheduleRevision(
                    event.getScheduleRevision()
            );

            if (participant.getUser()
                    .getUserId()
                    .equals(organizerId)) {
                participant.setStatus(
                        InvitationStatus.ACCEPTED
                );

                participant.setRespondedAt(now);
                participant.setInvitedAt(now);
                participant.setExpiresAt(
                        now.plusSeconds(
                            INVITATION_VALIDITY_HOURS * 3600L
                        )
                );
            } else {
                participant.setStatus(
                        InvitationStatus.PENDING
                );

                participant.setInvitedAt(now);
                participant.setExpiresAt(expiresAt);
                participant.setRespondedAt(null);
            }

            participant.setRemovedAt(null);
        }
    }

    private boolean isDifferentSchedule(
            CalendarEvent event,
            ValidatedCalendarSchedule proposal
    ) {
        return Boolean.TRUE.equals(event.getAllDay())
                    != proposal.allDay()
                || !Objects.equals(
                    event.getStartAt(),
                    proposal.startAt()
                )
                || !Objects.equals(
                    event.getEndAt(),
                    proposal.endAt()
                )
                || !Objects.equals(
                    event.getStartDate(),
                    proposal.startDate()
                )
                || !Objects.equals(
                    event.getEndDate(),
                    proposal.endDate()
                )
                || !Objects.equals(
                    event.getTimeZone(),
                    proposal.timeZone()
                );
    }

    private Instant scheduleStart(
            ValidatedCalendarSchedule schedule
    ) {
        if (!schedule.allDay()) {
            return schedule.startAt();
        }

        return schedule.startDate()
                .atStartOfDay(
                    ZoneId.of(
                        schedule.timeZone()
                    )
                )
                .toInstant();
    }

    private Instant eventStart(
            CalendarEvent event
    ) {
        if (!Boolean.TRUE.equals(event.getAllDay())) {
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

    private void validatePendingRequest(
            CalendarTimeChangeRequest request,
            Long expectedVersion
    ) {
        if (expectedVersion == null
                || !request.getVersion()
                    .equals(expectedVersion)) {
            throw new ConflictException(
                    "TIME_CHANGE_VERSION_CONFLICT",
                    "The time-change request was updated. "
                    + "Refresh and try again."
            );
        }

        if (request.getStatus()
                != TimeChangeRequestStatus.PENDING) {
            throw new ConflictException(
                    "TIME_CHANGE_REQUEST_NOT_PENDING",
                    "This request is no longer pending."
            );
        }
    }

    private void validateRequestVisibility(
            CalendarTimeChangeRequest request,
            Long userId
    ) {
        boolean requester =
                request.getRequester()
                        .getUserId()
                        .equals(userId);

        boolean organizer =
                request.getEvent()
                        .getOrganizer()
                        .getUserId()
                        .equals(userId);

        if (!requester && !organizer) {
            throw new ResourceNotFoundException(
                    "Time-change request not found."
            );
        }
    }

    private TimeChangeRequestStatus parseStatus(
            String status
    ) {
        if ("ALL".equals(status)) {
            return null;
        }

        try {
            return TimeChangeRequestStatus.valueOf(
                    status
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Invalid time-change request status."
            );
        }
    }

    private String normalizeOptional(
            String value
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private void validatePagination(
            int page,
            int size
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Invalid pagination values."
            );
        }
    }

    private void validateTimeChangeRequester(
        CalendarEventParticipant participant,
        CalendarEvent event,
        Instant now
) {
    InvitationStatus status =
            participant.getStatus();

    /*
     * A participant can request a different time:
     *
     * 1. Before accepting, while the invitation is
     *    still valid.
     * 2. After accepting, if another conflict appears.
     */
    if (status != InvitationStatus.PENDING
            && status != InvitationStatus.ACCEPTED) {
        throw new ForbiddenOperationException(
                "Only a pending or accepted participant "
                + "can request a time change."
        );
    }

    /*
     * A participant record belonging to an older event
     * schedule cannot be used.
     */
    if (!Objects.equals(
            participant.getScheduleRevision(),
            event.getScheduleRevision()
    )) {
        throw new ConflictException(
                "INVITATION_SCHEDULE_CHANGED",
                "The invitation belongs to an older event "
                + "schedule. Refresh the event and try again."
        );
    }

    /*
     * The five-hour expiry applies only while the
     * invitation is pending. Once accepted, the original
     * invitation expiry no longer prevents time-change
     * requests.
     */
    if (status == InvitationStatus.PENDING) {
        Instant expiresAt =
                participant.getExpiresAt();

        if (expiresAt == null
                || !expiresAt.isAfter(now)) {
            throw new ConflictException(
                    "INVITATION_EXPIRED",
                    "The invitation has expired. A time-change "
                    + "request can no longer be submitted."
            );
        }
    }
}
}
