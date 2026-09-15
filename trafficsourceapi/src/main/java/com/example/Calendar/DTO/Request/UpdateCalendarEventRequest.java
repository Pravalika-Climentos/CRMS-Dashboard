package com.example.Calendar.DTO.Request;

import com.example.Calendar.Entity.CalendarVisibility;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;

@Getter
public class UpdateCalendarEventRequest {

    @NotNull(message = "Expected version is required.")
    @PositiveOrZero(message = "Expected version cannot be negative.")
    private Long expectedVersion;

    @Size(
            max = 200,
            message = "Event title cannot exceed 200 characters."
    )
    private String title;

    @JsonIgnore
    private boolean titleProvided;

    @Size(
            max = 10000,
            message = "Description cannot exceed 10000 characters."
    )
    private String description;

    @JsonIgnore
    private boolean descriptionProvided;

    @Size(
            max = 255,
            message = "Location cannot exceed 255 characters."
    )
    private String location;

    @JsonIgnore
    private boolean locationProvided;

    @Size(
            max = 2048,
            message = "Meeting URL cannot exceed 2048 characters."
    )
    private String meetingUrl;

    @JsonIgnore
    private boolean meetingUrlProvided;

    @Positive
    private Long categoryId;

    @JsonIgnore
    private boolean categoryIdProvided;

    private Boolean allDay;

    @JsonIgnore
    private boolean allDayProvided;

    private Instant startAt;

    @JsonIgnore
    private boolean startAtProvided;

    private Instant endAt;

    @JsonIgnore
    private boolean endAtProvided;

    private LocalDate startDate;

    @JsonIgnore
    private boolean startDateProvided;

    private LocalDate endDate;

    @JsonIgnore
    private boolean endDateProvided;

    @Size(
            max = 64,
            message = "Timezone cannot exceed 64 characters."
    )
    private String timezone;

    @JsonIgnore
    private boolean timezoneProvided;

    private CalendarVisibility visibility;

    @JsonIgnore
    private boolean visibilityProvided;

    private Boolean blocksTime;

    @JsonIgnore
    private boolean blocksTimeProvided;

    @JsonSetter("expectedVersion")
    public void setExpectedVersion(Long expectedVersion) {
        this.expectedVersion = expectedVersion;
    }

    @JsonSetter("title")
    public void setTitle(String title) {
        this.title = title;
        this.titleProvided = true;
    }

    @JsonSetter("description")
    public void setDescription(String description) {
        this.description = description;
        this.descriptionProvided = true;
    }

   
    @JsonSetter("location")
    public void setLocation(String location) {
        this.location = location;
        this.locationProvided = true;
    }

  
    @JsonSetter("meetingUrl")
    public void setMeetingUrl(String meetingUrl) {
        this.meetingUrl = meetingUrl;
        this.meetingUrlProvided = true;
    }

    @JsonSetter("allDay")
    public void setAllDay(Boolean allDay) {
        this.allDay = allDay;
        this.allDayProvided = true;
    }

    @JsonSetter("startAt")
    public void setStartAt(Instant startAt) {
        this.startAt = startAt;
        this.startAtProvided = true;
    }

    @JsonSetter("endAt")
    public void setEndAt(Instant endAt) {
        this.endAt = endAt;
        this.endAtProvided = true;
    }

    @JsonSetter("startDate")
    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
        this.startDateProvided = true;
    }

    @JsonSetter("endDate")
    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
        this.endDateProvided = true;
    }

    @JsonSetter("timezone")
    public void setTimezone(String timezone) {
        this.timezone = timezone;
        this.timezoneProvided = true;
    }

    @JsonSetter("visibility")
    public void setVisibility(
            CalendarVisibility visibility
    ) {
        this.visibility = visibility;
        this.visibilityProvided = true;
    }

    @JsonSetter("blocksTime")
    public void setBlocksTime(Boolean blocksTime) {
        this.blocksTime = blocksTime;
        this.blocksTimeProvided = true;
    }

    public boolean isAllDayProvided() {
    return allDayProvided;
}

public boolean isStartAtProvided() {
    return startAtProvided;
}

public boolean isEndAtProvided() {
    return endAtProvided;
}

public boolean isStartDateProvided() {
    return startDateProvided;
}

public boolean isEndDateProvided() {
    return endDateProvided;
}

public boolean isTimezoneProvided() {
    return timezoneProvided;
}

public Long getCategoryId() {
    return categoryId;
}

@JsonSetter("categoryId")
public void setCategoryId(Long categoryId) {
    this.categoryId = categoryId;
    this.categoryIdProvided = true;
}

@JsonIgnore
public boolean isCategoryIdProvided() {
    return categoryIdProvided;
}
}