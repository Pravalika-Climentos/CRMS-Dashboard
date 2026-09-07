package com.example.Calendar.Controller;

import com.example.Calendar.DTO.Request.CreateTimeChangeRequest;
import com.example.Calendar.DTO.Request.DecideTimeChangeRequest;
import com.example.Calendar.DTO.Response.CalendarTimeChangeResponse;
import com.example.Calendar.Service.CalendarTimeChangeService;
import com.example.Common.DTO.Response.PageResponse;
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
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
@Validated
public class CalendarTimeChangeController {

    private final CalendarTimeChangeService service;

    @PostMapping(
        "/events/{eventId}/time-change-requests"
    )
    public ResponseEntity<CalendarTimeChangeResponse>
    create(
            @PathVariable
            @Positive
            Long eventId,

            @Valid
            @RequestBody
            CreateTimeChangeRequest request
    ) {
        CalendarTimeChangeResponse response =
                service.create(
                        eventId,
                        request
                );

        return ResponseEntity
                .created(
                    URI.create(
                        "/api/calendar/time-change-requests/"
                        + response.requestId()
                    )
                )
                .body(response);
    }

    @GetMapping("/time-change-requests")
    public ResponseEntity<
            PageResponse<CalendarTimeChangeResponse>>
    getRequests(
            @RequestParam(defaultValue = "received")
            String scope,

            @RequestParam(defaultValue = "ALL")
            String status,

            @RequestParam(defaultValue = "0")
            @PositiveOrZero
            int page,

            @RequestParam(defaultValue = "20")
            @Min(1)
            @Max(100)
            int size
    ) {
        return ResponseEntity.ok(
                service.getRequests(
                        scope,
                        status,
                        page,
                        size
                )
        );
    }

    @GetMapping(
        "/time-change-requests/{requestId}"
    )
    public ResponseEntity<CalendarTimeChangeResponse>
    getRequest(
            @PathVariable
            @Positive
            Long requestId
    ) {
        return ResponseEntity.ok(
                service.getRequest(requestId)
        );
    }

    @PatchMapping(
        "/time-change-requests/{requestId}/decision"
    )
    public ResponseEntity<CalendarTimeChangeResponse>
    decide(
            @PathVariable
            @Positive
            Long requestId,

            @Valid
            @RequestBody
            DecideTimeChangeRequest request
    ) {
        return ResponseEntity.ok(
                service.decide(
                        requestId,
                        request
                )
        );
    }

    @DeleteMapping(
        "/time-change-requests/{requestId}"
    )
    public ResponseEntity<Void> withdraw(
            @PathVariable
            @Positive
            Long requestId,

            @RequestParam
            @PositiveOrZero
            Long expectedVersion
    ) {
        service.withdraw(
                requestId,
                expectedVersion
        );

        return ResponseEntity
                .noContent()
                .build();
    }
}