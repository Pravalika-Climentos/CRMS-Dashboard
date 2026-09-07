package com.example.Calendar.DTO.Request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public class UpdateCalendarCategoryRequest {

    @NotNull(message = "Expected version is required.")
    @PositiveOrZero
    private Long expectedVersion;

    @Size(max = 100)
    private String name;

    @Pattern(
        regexp = "^#[0-9A-Fa-f]{6}$",
        message = "Color must use the format #RRGGBB."
    )
    private String color;

    private Boolean active;

    @JsonIgnore
    private boolean nameProvided;

    @JsonIgnore
    private boolean colorProvided;

    @JsonIgnore
    private boolean activeProvided;

    public Long getExpectedVersion() {
        return expectedVersion;
    }

    public void setExpectedVersion(
            Long expectedVersion
    ) {
        this.expectedVersion = expectedVersion;
    }

    public String getName() {
        return name;
    }

    @JsonSetter("name")
    public void setName(String name) {
        this.name = name;
        this.nameProvided = true;
    }

    public String getColor() {
        return color;
    }

    @JsonSetter("color")
    public void setColor(String color) {
        this.color = color;
        this.colorProvided = true;
    }

    public Boolean getActive() {
        return active;
    }

    @JsonSetter("active")
    public void setActive(Boolean active) {
        this.active = active;
        this.activeProvided = true;
    }

    @JsonIgnore
    public boolean isNameProvided() {
        return nameProvided;
    }

    @JsonIgnore
    public boolean isColorProvided() {
        return colorProvided;
    }

    @JsonIgnore
    public boolean isActiveProvided() {
        return activeProvided;
    }
}