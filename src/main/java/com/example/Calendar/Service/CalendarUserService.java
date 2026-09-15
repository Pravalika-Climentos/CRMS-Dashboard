package com.example.Calendar.Service;

import com.example.Calendar.DTO.Response.CalendarUserResponse;
import com.example.Common.DTO.Response.PageResponse;

public interface CalendarUserService {

    PageResponse<CalendarUserResponse> searchUsers(
            String search,
            int page,
            int size
    );
}