package com.example.Calendar.Controller;

import com.example.Calendar.DTO.Request.RespondInvitationRequest;
import com.example.Calendar.DTO.Response.CalendarInvitationResponse;
import com.example.Calendar.DTO.Response.InvitationSummaryResponse;
import com.example.Calendar.Service.CalendarInvitationService;
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

@RestController
@RequestMapping("/api/calendar/invitations")
@RequiredArgsConstructor
@Validated
public class CalendarInvitationController {

    private final CalendarInvitationService
            invitationService;

    @GetMapping
    public ResponseEntity<
            PageResponse<CalendarInvitationResponse>>
    getInvitations(
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
                invitationService.getInvitations(
                        status,
                        page,
                        size
                )
        );
    }

    @GetMapping("/summary")
    public ResponseEntity<InvitationSummaryResponse>
    getSummary() {
        return ResponseEntity.ok(
                invitationService.getSummary()
        );
    }

    @GetMapping("/{invitationId}")
    public ResponseEntity<CalendarInvitationResponse>
    getInvitation(
            @PathVariable
            @Positive
            Long invitationId
    ) {
        return ResponseEntity.ok(
                invitationService.getInvitation(
                        invitationId
                )
        );
    }

    @PatchMapping("/{invitationId}/response")
    public ResponseEntity<CalendarInvitationResponse>
    respond(
            @PathVariable
            @Positive
            Long invitationId,

            @Valid
            @RequestBody
            RespondInvitationRequest request
    ) {
        return ResponseEntity.ok(
                invitationService.respond(
                        invitationId,
                        request
                )
        );
    }
}