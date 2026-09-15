package com.example.Calendar.Mapper;

import com.example.Calendar.DTO.Response.CalendarTimeChangeResponse;
import com.example.Calendar.Entity.CalendarTimeChangeRequest;
import com.example.Calendar.Entity.TimeChangeRequestStatus;
import org.springframework.stereotype.Component;

@Component
public class CalendarTimeChangeMapper {

    private final CalendarEventMapper eventMapper;

    public CalendarTimeChangeMapper(
            CalendarEventMapper eventMapper
    ) {
        this.eventMapper = eventMapper;
    }

    public CalendarTimeChangeResponse toResponse(
            CalendarTimeChangeRequest request,
            Long currentUserId
    ) {
        boolean pending =
                request.getStatus()
                        == TimeChangeRequestStatus.PENDING;

        boolean organizer =
                request.getEvent()
                        .getOrganizer()
                        .getUserId()
                        .equals(currentUserId);

        boolean requester =
                request.getRequester()
                        .getUserId()
                        .equals(currentUserId);

        return new CalendarTimeChangeResponse(
                String.valueOf(request.getRequestId()),
                String.valueOf(
                        request.getEvent()
                                .getEventId()
                ),
                request.getEvent().getTitle(),
                eventMapper.toUserSummary(
                        request.getRequester()
                ),
                request.getScheduleRevision(),
                Boolean.TRUE.equals(
                        request.getProposedAllDay()
                ),
                request.getProposedStartAt(),
                request.getProposedEndAt(),
                request.getProposedStartDate(),
                request.getProposedEndDate(),
                request.getProposedTimeZone(),
                request.getReason(),
                request.getStatus(),
                eventMapper.toUserSummary(
                        request.getDecidedBy()
                ),
                request.getDecisionNote(),
                request.getDecidedAt(),
                request.getVersion(),
                request.getCreatedAt(),
                request.getUpdatedAt(),
                pending && organizer,
                pending && organizer,
                pending && requester
        );
    }
}