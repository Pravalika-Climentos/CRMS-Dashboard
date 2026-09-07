package com.example.Calendar.Repository.Projection;

import com.example.Calendar.Entity.CalendarVisibility;
import com.example.Calendar.Entity.InvitationStatus;

import java.time.Instant;
import java.time.LocalDate;

public interface CalendarAvailabilityEventProjection {

    Long getUserId();

    Long getEventId();

    Long getOrganizerId();

    String getTitle();

    CalendarVisibility getVisibility();

    Boolean getAllDay();

    Instant getStartAt();

    Instant getEndAt();

    LocalDate getStartDate();

    LocalDate getEndDate();

    String getTimeZone();

    /*
     * Null for organizer-owned event rows.
     *
     * ACCEPTED or PENDING for participant rows.
     */
    InvitationStatus getParticipationStatus();

    Instant getInvitationExpiresAt();
}