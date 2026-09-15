package com.example.Calendar.Service;

import com.example.Calendar.DTO.Request.CreateCalendarCategoryRequest;
import com.example.Calendar.DTO.Request.UpdateCalendarCategoryRequest;
import com.example.Calendar.DTO.Response.CalendarCategoryResponse;
import com.example.Common.DTO.Response.PageResponse;

public interface CalendarCategoryService {

    CalendarCategoryResponse create(
            CreateCalendarCategoryRequest request
    );

    PageResponse<CalendarCategoryResponse> getCategories(
            String search,
            boolean includeInactive,
            int page,
            int size
    );

    CalendarCategoryResponse update(
            Long categoryId,
            UpdateCalendarCategoryRequest request
    );
}