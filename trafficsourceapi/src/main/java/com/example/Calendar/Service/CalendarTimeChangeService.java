package com.example.Calendar.Service;

import com.example.Calendar.DTO.Request.CreateTimeChangeRequest;
import com.example.Calendar.DTO.Request.DecideTimeChangeRequest;
import com.example.Calendar.DTO.Response.CalendarTimeChangeResponse;
import com.example.Common.DTO.Response.PageResponse;

public interface CalendarTimeChangeService {

    CalendarTimeChangeResponse create(
            Long eventId,
            CreateTimeChangeRequest request
    );

    PageResponse<CalendarTimeChangeResponse> getRequests(
            String scope,
            String status,
            int page,
            int size
    );

    CalendarTimeChangeResponse getRequest(
            Long requestId
    );

    CalendarTimeChangeResponse decide(
            Long requestId,
            DecideTimeChangeRequest request
    );

    void withdraw(
            Long requestId,
            Long expectedVersion
    );
}