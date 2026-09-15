package com.example.Calendar.Mapper;

import com.example.CRM.Entity.User;
import com.example.Calendar.DTO.Response.CalendarCategoryResponse;
import com.example.Calendar.DTO.Response.CalendarEventParticipantResponse;
import com.example.Calendar.DTO.Response.CalendarEventPermissionsResponse;
import com.example.Calendar.DTO.Response.CalendarEventResponse;
import com.example.Calendar.Entity.CalendarCategory;
import com.example.Calendar.Entity.CalendarEvent;
import com.example.Calendar.Entity.CalendarEventParticipant;
import com.example.Calendar.Entity.CalendarEventStatus;
import com.example.Calendar.Entity.InvitationStatus;
import com.example.Calendar.Validation.ValidatedCalendarSchedule;
import com.example.Common.DTO.Response.UserSummaryResponse;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class CalendarEventMapper {

    public CalendarEventResponse toResponse(
            CalendarEvent event,
            long participantCount,
            Long currentUserId,
            CalendarEventParticipant currentUserParticipant
    ) {
        boolean organizer =
                isSameUser(
                        event.getOrganizer(),
                        currentUserId
                );

        boolean scheduled =
                event.getStatus()
                        == CalendarEventStatus.SCHEDULED;

        boolean pendingInvitation =
                currentUserParticipant != null
                        && currentUserParticipant.getStatus()
                        == InvitationStatus.PENDING
                        && !isExpired(
                                currentUserParticipant,
                                Instant.now()
                        );

        boolean acceptedParticipant =
                currentUserParticipant != null
                        && currentUserParticipant.getStatus()
                        == InvitationStatus.ACCEPTED;

        CalendarEventPermissionsResponse permissions =
                new CalendarEventPermissionsResponse(
                        organizer && scheduled,
                        organizer && scheduled,
                        organizer && scheduled,
                        !organizer
                                && scheduled
                                && acceptedParticipant,
                        !organizer
                                && scheduled
                                && pendingInvitation
                );

        return new CalendarEventResponse(
                String.valueOf(event.getEventId()),
                event.getTitle(),
                event.getDescription(),
                event.getLocation(),
                event.getMeetingUrl(),
                toCategoryResponse(event.getCategory()),
                Boolean.TRUE.equals(event.getAllDay()),
                event.getStartAt(),
                event.getEndAt(),
                event.getStartDate(),
                event.getEndDate(),
                event.getTimeZone(),
                event.getVisibility(),
                Boolean.TRUE.equals(event.getBlocksTime()),
                event.getStatus(),
                event.getScheduleRevision(),
                event.getVersion(),
                toUserSummary(event.getOrganizer()),
                participantCount,
                permissions,
                event.getCancelledAt(),
                event.getCreatedAt(),
                event.getUpdatedAt()
        );
    }

    public CalendarEventParticipantResponse toParticipantResponse(
            CalendarEventParticipant participant,
            Long currentUserId,
            Instant now
    ) {
        boolean expired =
                isExpired(
                        participant,
                        now
                );

        boolean correctUser =
                isSameUser(
                        participant.getUser(),
                        currentUserId
                );

        boolean currentScheduleRevision =
                participant.getScheduleRevision()
                        .equals(
                                participant
                                        .getEvent()
                                        .getScheduleRevision()
                        );

        boolean eventScheduled =
                participant
                        .getEvent()
                        .getStatus()
                        == CalendarEventStatus.SCHEDULED;

        boolean canRespond =
                correctUser
                        && participant.getStatus()
                        == InvitationStatus.PENDING
                        && !expired
                        && currentScheduleRevision
                        && eventScheduled;

        return new CalendarEventParticipantResponse(
                String.valueOf(
                        participant.getParticipantId()
                ),
                String.valueOf(
                        participant
                                .getEvent()
                                .getEventId()
                ),
                toUserSummary(participant.getUser()),
                participant.getStatus(),
                toUserSummary(participant.getInvitedBy()),
                participant.getInvitedAt(),
                participant.getExpiresAt(),
                participant.getRespondedAt(),
                participant.getRemovedAt(),
                participant.getScheduleRevision(),
                participant.getVersion(),
                expired,
                canRespond
        );
    }

    public UserSummaryResponse toUserSummary(
            User user
    ) {
        if (user == null) {
            return null;
        }

        return new UserSummaryResponse(
                String.valueOf(user.getUserId()),
                user.getFullName(),
                user.getDesignation(),
                user.getAvatar(),
                user.getActive()
        );
    }

    public void applySchedule(
            CalendarEvent event,
            ValidatedCalendarSchedule schedule
    ) {
        event.setAllDay(schedule.allDay());

        event.setStartAt(schedule.startAt());
        event.setEndAt(schedule.endAt());

        event.setStartDate(schedule.startDate());
        event.setEndDate(schedule.endDate());

        event.setTimeZone(schedule.timeZone());
    }

    private boolean isSameUser(
            User user,
            Long userId
    ) {
        return user != null
                && user.getUserId() != null
                && user.getUserId().equals(userId);
    }

    private boolean isExpired(
            CalendarEventParticipant participant,
            Instant now
    ) {
        return participant.getStatus()
                        == InvitationStatus.PENDING
                && participant.getExpiresAt() != null
                && !now.isBefore(
                        participant.getExpiresAt()
                );
    }

    public CalendarCategoryResponse toCategoryResponse(
        CalendarCategory category
) {
    if (category == null) {
        return null;
    }

    return new CalendarCategoryResponse(
            String.valueOf(category.getCategoryId()),
            category.getName(),
            category.getColor(),
            Boolean.TRUE.equals(category.getActive()),
            category.getVersion(),
            category.getCreatedAt(),
            category.getUpdatedAt()
    );
}
}