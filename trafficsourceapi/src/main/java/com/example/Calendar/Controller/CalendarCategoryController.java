package com.example.Calendar.Controller;

import com.example.Calendar.DTO.Request.CreateCalendarCategoryRequest;
import com.example.Calendar.DTO.Request.UpdateCalendarCategoryRequest;
import com.example.Calendar.DTO.Response.CalendarCategoryResponse;
import com.example.Calendar.Service.CalendarCategoryService;
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
@RequestMapping("/api/calendar/categories")
@RequiredArgsConstructor
@Validated
public class CalendarCategoryController {

    private final CalendarCategoryService service;

    @PostMapping
    public ResponseEntity<CalendarCategoryResponse> create(
            @Valid
            @RequestBody
            CreateCalendarCategoryRequest request
    ) {
        CalendarCategoryResponse response =
                service.create(request);

        return ResponseEntity
                .created(
                    URI.create(
                        "/api/calendar/categories/"
                        + response.categoryId()
                    )
                )
                .body(response);
    }

    @GetMapping
    public ResponseEntity<
            PageResponse<CalendarCategoryResponse>>
    getCategories(
            @RequestParam(defaultValue = "")
            String search,

            @RequestParam(defaultValue = "false")
            boolean includeInactive,

            @RequestParam(defaultValue = "0")
            @PositiveOrZero
            int page,

            @RequestParam(defaultValue = "20")
            @Min(1)
            @Max(100)
            int size
    ) {
        return ResponseEntity.ok(
                service.getCategories(
                        search,
                        includeInactive,
                        page,
                        size
                )
        );
    }

    @PatchMapping("/{categoryId}")
    public ResponseEntity<CalendarCategoryResponse> update(
            @PathVariable
            @Positive
            Long categoryId,

            @Valid
            @RequestBody
            UpdateCalendarCategoryRequest request
    ) {
        return ResponseEntity.ok(
                service.update(
                        categoryId,
                        request
                )
        );
    }
}