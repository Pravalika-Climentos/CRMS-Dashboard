package com.example.Calendar.Service;

import com.example.Calendar.DTO.Request.InvitationDecision;
import com.example.Calendar.DTO.Request.RespondInvitationRequest;
import com.example.Calendar.DTO.Response.CalendarInvitationResponse;
import com.example.Calendar.DTO.Response.InvitationSummaryResponse;
import com.example.Calendar.Entity.CalendarEvent;
import com.example.Calendar.Entity.CalendarEventParticipant;
import com.example.Calendar.Entity.CalendarEventStatus;
import com.example.Calendar.Entity.InvitationStatus;
import com.example.Calendar.Mapper.CalendarEventMapper;
import com.example.Calendar.Repository.CalendarEventParticipantRepository;
import com.example.Calendar.Repository.CalendarEventRepository;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarInvitationServiceImpl
        implements CalendarInvitationService {

    private final CalendarEventParticipantRepository
            participantRepository;

    private final CalendarEventRepository
            eventRepository;

    private final CalendarEventMapper calendarEventMapper;

    private final CurrentUserService currentUserService;

    private final InternalEmailNotificationService emailNotifications;

    @Override
    @Transactional
    public PageResponse<CalendarInvitationResponse>
    getInvitations(
            String status,
            int page,
            int size
    ) {
        validatePagination(page, size);

        Long currentUserId =
                currentUserService.getCurrentUserId();

        expireInvitations(
                currentUserId
        );

        String normalizedStatus =
                status == null
                        ? "ALL"
                        : status.trim().toUpperCase();

        Pageable pageable =
                PageRequest.of(page, size);

        Page<CalendarEventParticipant> invitations;

        if ("ALL".equals(normalizedStatus)) {
            invitations =
                    participantRepository
                            .findAllInvitations(
                                    currentUserId,
                                    pageable
                            );
        } else {
            InvitationStatus invitationStatus;

            try {
                invitationStatus =
                        InvitationStatus.valueOf(
                                normalizedStatus
                        );
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "Invalid invitation status."
                );
            }

            invitations =
                    participantRepository
                            .findInvitationsByStatus(
                                    currentUserId,
                                    invitationStatus,
                                    pageable
                            );
        }

        Instant now = Instant.now();

        List<CalendarInvitationResponse> responses =
                invitations.getContent()
                        .stream()
                        .map(invitation ->
                                toResponse(
                                        invitation,
                                        currentUserId,
                                        now,
                                        List.of()
                                )
                        )
                        .toList();

        return new PageResponse<>(
                responses,
                invitations.getNumber(),
                invitations.getSize(),
                invitations.getTotalElements(),
                invitations.getTotalPages(),
                invitations.hasNext()
        );
    }

    @Override
    @Transactional
    public InvitationSummaryResponse getSummary() {
        Long currentUserId =
                currentUserService.getCurrentUserId();

        expireInvitations(
                currentUserId
        );

        return new InvitationSummaryResponse(
                count(
                        currentUserId,
                        InvitationStatus.PENDING
                ),
                count(
                        currentUserId,
                        InvitationStatus.ACCEPTED
                ),
                count(
                        currentUserId,
                        InvitationStatus.DECLINED
                ),
                count(
                        currentUserId,
                        InvitationStatus.EXPIRED
                )
        );
    }

    @Override
    @Transactional
    public CalendarInvitationResponse getInvitation(
            Long invitationId
    ) {
        Long currentUserId =
                currentUserService.getCurrentUserId();

        expireInvitations(
                currentUserId
        );

        CalendarEventParticipant invitation =
                participantRepository
                        .findByParticipantIdAndUserUserId(
                                invitationId,
                                currentUserId
                        )
                        .orElseThrow(
                                () ->
                                    new ResourceNotFoundException(
                                        "Invitation not found."
                                    )
                        );

        validateRealInvitation(
                invitation,
                currentUserId
        );

        List<String> conflictingEventIds =
                findConflictIds(
                        invitation.getEvent(),
                        currentUserId
                );

        return toResponse(
                invitation,
                currentUserId,
                Instant.now(),
                conflictingEventIds
        );
    }

    @Override
    @Transactional
    public CalendarInvitationResponse respond(
            Long invitationId,
            RespondInvitationRequest request
    ) {
        Long currentUserId =
                currentUserService.getCurrentUserId();

        CalendarEventParticipant invitation =
                participantRepository
                        .findByParticipantIdForUpdate(
                                invitationId
                        )
                        .orElseThrow(
                                () ->
                                    new ResourceNotFoundException(
                                        "Invitation not found."
                                    )
                        );

        validateRealInvitation(
                invitation,
                currentUserId
        );

        if (!invitation.getVersion()
                .equals(request.expectedVersion())) {
            throw new ConflictException(
                    "INVITATION_VERSION_CONFLICT",
                    "The invitation was changed by another request. "
                    + "Refresh and try again."
            );
        }

        CalendarEvent event =
                invitation.getEvent();

        if (event.getStatus()
                != CalendarEventStatus.SCHEDULED) {
            throw new ConflictException(
                    "EVENT_NOT_ACTIVE",
                    "The event is no longer active."
            );
        }

        if (!invitation.getScheduleRevision()
                .equals(event.getScheduleRevision())) {
            throw new ConflictException(
                    "INVITATION_SCHEDULE_CHANGED",
                    "The event schedule changed. "
                    + "Refresh the invitation."
            );
        }

        Instant now = Instant.now();

        if (invitation.getExpiresAt() != null
                && !now.isBefore(
                        invitation.getExpiresAt()
                )) {
            throw new ConflictException(
                    "INVITATION_EXPIRED",
                    "The invitation has expired."
            );
        }

        if (invitation.getStatus()
                != InvitationStatus.PENDING) {
            throw new ConflictException(
                    "INVITATION_ALREADY_RESPONDED",
                    "The invitation has already been answered."
            );
        }

        List<String> conflictIds =
                findConflictIds(
                        event,
                        currentUserId
                );

        if (request.response()
                    == InvitationDecision.ACCEPTED
                && !conflictIds.isEmpty()
                && !request.conflictsAcknowledged()) {
            throw new ConflictException(
                    "CALENDAR_CONFLICT_ACKNOWLEDGEMENT_REQUIRED",
                    "This event overlaps with events "
                    + conflictIds
                    + ". Resend with acknowledgeConflicts=true "
                    + "to accept it."
            );
        }

        invitation.setStatus(
                request.response()
                        == InvitationDecision.ACCEPTED
                        ? InvitationStatus.ACCEPTED
                        : InvitationStatus.DECLINED
        );

        invitation.setRespondedAt(now);

        invitation =
                participantRepository
                        .saveAndFlush(invitation);

        emailNotifications.send(invitation.getUser(), List.of(event.getOrganizer()),
                "Invitation response: " + event.getTitle(),
                invitation.getUser().getFullName() + " " + invitation.getStatus().name().toLowerCase()
                        + " the invitation to “" + event.getTitle() + "”.");

        return toResponse(
                invitation,
                currentUserId,
                now,
                conflictIds
        );
    }

    private CalendarInvitationResponse toResponse(
            CalendarEventParticipant invitation,
            Long currentUserId,
            Instant now,
            List<String> conflictingEventIds
    ) {
        CalendarEvent event =
                invitation.getEvent();

        boolean expired =
                invitation.getStatus()
                        == InvitationStatus.EXPIRED
                || (
                    invitation.getStatus()
                            == InvitationStatus.PENDING
                    && invitation.getExpiresAt() != null
                    && !now.isBefore(
                            invitation.getExpiresAt()
                    )
                );

        boolean canRespond =
                invitation.getUser()
                        .getUserId()
                        .equals(currentUserId)
                && invitation.getStatus()
                        == InvitationStatus.PENDING
                && !expired
                && event.getStatus()
                        == CalendarEventStatus.SCHEDULED
                && invitation.getScheduleRevision()
                        .equals(
                                event.getScheduleRevision()
                        );

        return new CalendarInvitationResponse(
                String.valueOf(
                        invitation.getParticipantId()
                ),
                String.valueOf(event.getEventId()),
                event.getTitle(),
                event.getDescription(),
                event.getLocation(),
                event.getMeetingUrl(),
                Boolean.TRUE.equals(event.getAllDay()),
                event.getStartAt(),
                event.getEndAt(),
                event.getStartDate(),
                event.getEndDate(),
                event.getTimeZone(),
                event.getStatus(),
                expired
                        ? InvitationStatus.EXPIRED
                        : invitation.getStatus(),
                calendarEventMapper.toUserSummary(
                        event.getOrganizer()
                ),
                calendarEventMapper.toUserSummary(
                        invitation.getInvitedBy()
                ),
                invitation.getInvitedAt(),
                invitation.getExpiresAt(),
                invitation.getRespondedAt(),
                invitation.getScheduleRevision(),
                invitation.getVersion(),
                expired,
                canRespond,
                conflictingEventIds
        );
    }

    private List<String> findConflictIds(
            CalendarEvent event,
            Long userId
    ) {
        ZoneId zone =
                ZoneId.of(event.getTimeZone());

        Instant fromInstant;
        Instant toInstant;
        LocalDate fromDate;
        LocalDate toDate;

        if (Boolean.TRUE.equals(event.getAllDay())) {
            fromDate = event.getStartDate();
            toDate = event.getEndDate();

            fromInstant =
                    fromDate.atStartOfDay(zone)
                            .toInstant();

            toInstant =
                    toDate.atStartOfDay(zone)
                            .toInstant();
        } else {
            fromInstant = event.getStartAt();
            toInstant = event.getEndAt();

            fromDate =
                    fromInstant.atZone(zone)
                            .toLocalDate();

            ZonedDateTime localEnd =
                    toInstant.atZone(zone);

            toDate =
                    localEnd.toLocalTime()
                            .equals(LocalTime.MIDNIGHT)
                    ? localEnd.toLocalDate()
                    : localEnd.toLocalDate()
                            .plusDays(1);
        }

        Set<Long> eventIds =
                new LinkedHashSet<>();

        eventRepository
                .findBlockingTimedEventsForUsers(
                        List.of(userId),
                        CalendarEventStatus.SCHEDULED,
                        fromInstant,
                        toInstant
                )
                .forEach(conflict ->
                        eventIds.add(
                                conflict.getEventId()
                        )
                );

        eventRepository
                .findBlockingAllDayEventsForUsers(
                        List.of(userId),
                        CalendarEventStatus.SCHEDULED,
                        fromDate,
                        toDate
                )
                .forEach(conflict ->
                        eventIds.add(
                                conflict.getEventId()
                        )
                );

        eventIds.remove(event.getEventId());

        return eventIds.stream()
                .map(String::valueOf)
                .toList();
    }

    private void validateRealInvitation(
            CalendarEventParticipant invitation,
            Long currentUserId
    ) {
        if (!invitation.getUser()
                .getUserId()
                .equals(currentUserId)) {
            throw new ResourceNotFoundException(
                    "Invitation not found."
            );
        }

        if (invitation.getEvent()
                .getOrganizer()
                .getUserId()
                .equals(currentUserId)) {
            throw new ForbiddenOperationException(
                    "The organizer participant row "
                    + "is not an invitation."
            );
        }
    }

    private void expireInvitations(
            Long currentUserId
    ) {
        participantRepository
                .expirePendingInvitations(
                        currentUserId,
                        Instant.now()
                );
    }

    private long count(
            Long userId,
            InvitationStatus status
    ) {
        return participantRepository
                .countInvitationsByStatus(
                        userId,
                        status
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
