package com.example.Calendar.Controller;

import com.example.Calendar.DTO.Response.CalendarUserResponse;
import com.example.Calendar.Service.CalendarUserService;
import com.example.Common.DTO.Response.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/calendar/users")
@RequiredArgsConstructor
@Validated
public class CalendarUserController {

    private final CalendarUserService calendarUserService;

    @GetMapping
    public ResponseEntity<PageResponse<CalendarUserResponse>>
    searchUsers(
            @RequestParam(defaultValue = "")
            String search,

            @RequestParam(defaultValue = "0")
            @PositiveOrZero
            int page,

            @RequestParam(defaultValue = "20")
            @Min(1)
            @Max(100)
            int size
    ) {
        return ResponseEntity.ok(
                calendarUserService.searchUsers(
                        search,
                        page,
                        size
                )
        );
    }
}