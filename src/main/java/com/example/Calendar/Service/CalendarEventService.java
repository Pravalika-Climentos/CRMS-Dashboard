package com.example.Calendar.Service;

import java.time.Instant;
import java.util.List;

import com.example.Calendar.DTO.Request.AddEventParticipantsRequest;
import com.example.Calendar.DTO.Request.CreateCalendarEventRequest;
import com.example.Calendar.DTO.Request.UpdateCalendarEventRequest;
import com.example.Calendar.DTO.Response.CalendarEventParticipantResponse;
import com.example.Calendar.DTO.Response.CalendarEventResponse;
import com.example.Calendar.DTO.Response.ParticipantMutationResponse;
import com.example.Common.DTO.Response.PageResponse;

public interface CalendarEventService {

    CalendarEventResponse createEvent(
            CreateCalendarEventRequest request
    );

    CalendarEventResponse getEvent(
        Long eventId
    );

    List<CalendarEventResponse> getEvents(
        Instant from,
        Instant to
    );

    List<CalendarEventResponse> getUpcomingEvents(
        int limit
    );

    CalendarEventResponse updateEvent(
        Long eventId,
        UpdateCalendarEventRequest request
);

void cancelEvent(
        Long eventId,
        Long expectedVersion
);

ParticipantMutationResponse addParticipants(
        Long eventId,
        AddEventParticipantsRequest request
);

void removeParticipant(
        Long eventId,
        Long userId,
        Long expectedVersion
);

PageResponse<CalendarEventParticipantResponse>
getParticipants(
        Long eventId,
        int page,
        int size
);

}