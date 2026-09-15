package com.example.Calendar.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCalendarCategoryRequest(

        @NotBlank(message = "Category name is required.")
        @Size(max = 100)
        String name,

        @NotBlank(message = "Category color is required.")
        @Pattern(
            regexp = "^#[0-9A-Fa-f]{6}$",
            message = "Color must use the format #RRGGBB."
        )
        String color

) {
}