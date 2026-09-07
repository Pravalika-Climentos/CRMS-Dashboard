package com.example.Calendar.Service;

import com.example.Calendar.DTO.Request.RespondInvitationRequest;
import com.example.Calendar.DTO.Response.CalendarInvitationResponse;
import com.example.Calendar.DTO.Response.InvitationSummaryResponse;
import com.example.Common.DTO.Response.PageResponse;

public interface CalendarInvitationService {

    PageResponse<CalendarInvitationResponse>
    getInvitations(
            String status,
            int page,
            int size
    );

    InvitationSummaryResponse getSummary();

    CalendarInvitationResponse getInvitation(
            Long invitationId
    );

    CalendarInvitationResponse respond(
            Long invitationId,
            RespondInvitationRequest request
    );
}