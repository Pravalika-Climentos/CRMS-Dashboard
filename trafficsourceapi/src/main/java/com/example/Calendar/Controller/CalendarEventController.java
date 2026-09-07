package com.example.Calendar.Controller;

import com.example.Calendar.DTO.Request.AddEventParticipantsRequest;
import com.example.Calendar.DTO.Request.CreateCalendarEventRequest;
import com.example.Calendar.DTO.Request.UpdateCalendarEventRequest;
import com.example.Calendar.DTO.Response.CalendarEventParticipantResponse;
import com.example.Calendar.DTO.Response.CalendarEventResponse;
import com.example.Calendar.DTO.Response.ParticipantMutationResponse;
import com.example.Calendar.Service.CalendarEventService;
import com.example.Common.DTO.Response.PageResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/calendar/events")
@RequiredArgsConstructor
@Validated
public class CalendarEventController {

    private final CalendarEventService calendarEventService;

    /*
     * POST /api/calendar/events
     */
    @PostMapping
    public ResponseEntity<CalendarEventResponse> createEvent(
            @Valid
            @RequestBody
            CreateCalendarEventRequest request
    ) {
        CalendarEventResponse response =
                calendarEventService.createEvent(
                        request
                );

        URI location =
                URI.create(
                        "/api/calendar/events/"
                        + response.eventId()
                );

        return ResponseEntity
                .created(location)
                .body(response);
    }

    @GetMapping("/{eventId}")
public ResponseEntity<CalendarEventResponse> getEvent(
        @PathVariable
        @Positive
        Long eventId
) {
    return ResponseEntity.ok(
            calendarEventService.getEvent(
                    eventId
            )
    );
}

@GetMapping
public ResponseEntity<List<CalendarEventResponse>>
getEvents(
        @RequestParam
        @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE_TIME
        )
        Instant from,

        @RequestParam
        @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE_TIME
        )
        Instant to
) {
    return ResponseEntity.ok(
            calendarEventService.getEvents(
                    from,
                    to
            )
    );
}

@GetMapping("/upcoming")
public ResponseEntity<List<CalendarEventResponse>>
getUpcomingEvents(
        @RequestParam(defaultValue = "5")
        @Min(1)
        @Max(50)
        int limit
) {
    return ResponseEntity.ok(
            calendarEventService
                    .getUpcomingEvents(limit)
    );
}

@PatchMapping("/{eventId}")
public ResponseEntity<CalendarEventResponse> updateEvent(
        @PathVariable
        @Positive
        Long eventId,

        @Valid
        @RequestBody
        UpdateCalendarEventRequest request
) {
    return ResponseEntity.ok(
            calendarEventService.updateEvent(
                    eventId,
                    request
            )
    );
}

@DeleteMapping("/{eventId}")
public ResponseEntity<Void> cancelEvent(
        @PathVariable
        @Positive
        Long eventId,

        @RequestParam
        @PositiveOrZero
        Long expectedVersion
) {
    calendarEventService.cancelEvent(
            eventId,
            expectedVersion
    );

    return ResponseEntity
            .noContent()
            .build();
}

@PostMapping("/{eventId}/participants")
public ResponseEntity<ParticipantMutationResponse>
addParticipants(
        @PathVariable
        @Positive
        Long eventId,

        @Valid
        @RequestBody
        AddEventParticipantsRequest request
) {
    return ResponseEntity.ok(
            calendarEventService.addParticipants(
                    eventId,
                    request
            )
    );
}

@DeleteMapping(
        "/{eventId}/participants/{userId}"
)
public ResponseEntity<Void> removeParticipant(
        @PathVariable
        @Positive
        Long eventId,

        @PathVariable
        @Positive
        Long userId,

        @RequestParam
        @PositiveOrZero
        Long expectedVersion
) {
    calendarEventService.removeParticipant(
            eventId,
            userId,
            expectedVersion
    );

    return ResponseEntity
            .noContent()
            .build();
}

@GetMapping("/{eventId}/participants")
public ResponseEntity<
        PageResponse<CalendarEventParticipantResponse>>
getParticipants(
        @PathVariable
        @Positive
        Long eventId,

        @RequestParam(defaultValue = "0")
        @PositiveOrZero
        int page,

        @RequestParam(defaultValue = "20")
        @Min(1)
        @Max(100)
        int size
) {
    return ResponseEntity.ok(
            calendarEventService.getParticipants(
                    eventId,
                    page,
                    size
            )
    );
}
}