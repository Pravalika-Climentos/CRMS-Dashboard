package com.example.Calendar.Controller;

import com.example.Calendar.DTO.Request.AvailabilityCheckRequest;
import com.example.Calendar.DTO.Request.AvailabilitySuggestionsRequest;
import com.example.Calendar.DTO.Request.ExactAvailabilityCheckRequest;
import com.example.Calendar.DTO.Response.AvailabilityCheckResponse;
import com.example.Calendar.DTO.Response.AvailabilitySuggestionsResponse;
import com.example.Calendar.DTO.Response.ExactAvailabilityCheckResponse;
import com.example.Calendar.DTO.Response.TeamCalendarResponse;
import com.example.Calendar.Service.CalendarAvailabilityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Set;

@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
@Validated
public class CalendarAvailabilityController {

    private final CalendarAvailabilityService
            availabilityService;

    @GetMapping("/team-calendar")
    public ResponseEntity<TeamCalendarResponse>
    getTeamCalendar(
            @RequestParam(required = false)
            Set<@Positive Long> userIds,

            @RequestParam(required = false)
            Set<@Positive Long> teamIds,

            @RequestParam(
                required = false,
                defaultValue = "7"
            )
            @Min(1)
            @Max(31)
            Integer workingDays,

            @RequestParam(required = false)
            @DateTimeFormat(
                iso = DateTimeFormat.ISO.DATE_TIME
            )
            Instant from,

            @RequestParam(required = false)
            @DateTimeFormat(
                iso = DateTimeFormat.ISO.DATE_TIME
            )
            Instant to
    ) {
        return ResponseEntity.ok(
                availabilityService.getTeamCalendar(
                        userIds,
                        teamIds,
                        workingDays,
                        from,
                        to
                )
        );
    }

    @PostMapping("/availability/check")
    public ResponseEntity<AvailabilityCheckResponse>
    checkAvailability(
            @Valid
            @RequestBody
            AvailabilityCheckRequest request
    ) {
        return ResponseEntity.ok(
                availabilityService
                        .checkAvailability(request)
        );
    }

    @PostMapping("/availability/suggestions")
    public ResponseEntity<AvailabilitySuggestionsResponse>
    getAvailabilitySuggestions(
        @Valid
        @RequestBody
        AvailabilitySuggestionsRequest request
     ) {
    return ResponseEntity.ok(
            availabilityService
                    .getAvailabilitySuggestions(request)
    );
  }

  @PostMapping("/availability/exact-check")
public ResponseEntity<ExactAvailabilityCheckResponse>
checkExactAvailability(
        @Valid
        @RequestBody
        ExactAvailabilityCheckRequest request
) {
    return ResponseEntity.ok(
            availabilityService
                    .checkExactAvailability(request)
    );
}
}