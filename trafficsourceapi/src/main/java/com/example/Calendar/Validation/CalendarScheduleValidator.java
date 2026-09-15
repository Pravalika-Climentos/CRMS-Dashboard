package com.example.Calendar.Validation;

import com.example.Calendar.DTO.Request.CreateCalendarEventRequest;
import com.example.Calendar.DTO.Request.UpdateCalendarEventRequest;
import com.example.Calendar.Entity.CalendarEvent;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class CalendarScheduleValidator {

    private static final String DEFAULT_TIME_ZONE =
            "Asia/Kolkata";

    public ValidatedCalendarSchedule validateForCreate(
            CreateCalendarEventRequest request
    ) {
        String timeZone =
                normalizeAndValidateTimeZone(
                        request.timezone()
                );

        if (request.allDay()) {
            return validateAllDay(
                    request.startDate(),
                    request.endDate(),
                    timeZone
            );
        }

        return validateTimed(
                request.startAt(),
                request.endAt(),
                timeZone
        );
    }

    public ValidatedCalendarSchedule validateForUpdate(
            CalendarEvent event,
            UpdateCalendarEventRequest request
    ) {
        boolean allDay =
                request.isAllDayProvided()
                        ? requireAllDayValue(
                                request.getAllDay()
                        )
                        : Boolean.TRUE.equals(
                                event.getAllDay()
                        );

        String timeZone =
                request.isTimezoneProvided()
                        ? normalizeAndValidateTimeZone(
                                request.getTimezone()
                        )
                        : normalizeAndValidateTimeZone(
                                event.getTimeZone()
                        );

        if (allDay) {
            LocalDate startDate =
                    request.isStartDateProvided()
                            ? request.getStartDate()
                            : event.getStartDate();

            LocalDate endDate =
                    request.isEndDateProvided()
                            ? request.getEndDate()
                            : event.getEndDate();

            /*
             * When changing a timed event into an all-day event,
             * incompatible timed values are cleared automatically.
             */
            return validateAllDay(
                    startDate,
                    endDate,
                    timeZone
            );
        }

        Instant startAt =
                request.isStartAtProvided()
                        ? request.getStartAt()
                        : event.getStartAt();

        Instant endAt =
                request.isEndAtProvided()
                        ? request.getEndAt()
                        : event.getEndAt();

        /*
         * When changing an all-day event into a timed event,
         * incompatible date-only values are cleared automatically.
         */
        return validateTimed(
                startAt,
                endAt,
                timeZone
        );
    }

    private ValidatedCalendarSchedule validateTimed(
            Instant startAt,
            Instant endAt,
            String timeZone
    ) {
        if (startAt == null) {
            throw new IllegalArgumentException(
                    "startAt is required for a timed event."
            );
        }

        if (endAt == null) {
            throw new IllegalArgumentException(
                    "endAt is required for a timed event."
            );
        }

        if (!endAt.isAfter(startAt)) {
            throw new IllegalArgumentException(
                    "endAt must be after startAt."
            );
        }

        return new ValidatedCalendarSchedule(
                false,
                startAt,
                endAt,
                null,
                null,
                timeZone
        );
    }

    private ValidatedCalendarSchedule validateAllDay(
            LocalDate startDate,
            LocalDate endDate,
            String timeZone
    ) {
        if (startDate == null) {
            throw new IllegalArgumentException(
                    "startDate is required for an all-day event."
            );
        }

        if (endDate == null) {
            throw new IllegalArgumentException(
                    "endDate is required for an all-day event."
            );
        }

        if (!endDate.isAfter(startDate)) {
            throw new IllegalArgumentException(
                    "endDate must be after startDate. "
                    + "The all-day event end date is exclusive."
            );
        }

        return new ValidatedCalendarSchedule(
                true,
                null,
                null,
                startDate,
                endDate,
                timeZone
        );
    }

    private boolean requireAllDayValue(
            Boolean allDay
    ) {
        if (allDay == null) {
            throw new IllegalArgumentException(
                    "allDay cannot be null."
            );
        }

        return allDay;
    }

    private String normalizeAndValidateTimeZone(
            String timeZone
    ) {
        String normalized =
                timeZone == null
                        || timeZone.isBlank()
                        ? DEFAULT_TIME_ZONE
                        : timeZone.trim();

        if (normalized.length() > 64) {
            throw new IllegalArgumentException(
                    "Timezone cannot exceed 64 characters."
            );
        }

        try {
            ZoneId.of(normalized);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException(
                    "Invalid timezone: " + normalized
            );
        }

        return normalized;
    }

    public ValidatedCalendarSchedule validateProposal(
        Boolean allDay,
        Instant startAt,
        Instant endAt,
        LocalDate startDate,
        LocalDate endDate,
        String timeZone
) {
    if (allDay == null) {
        throw new IllegalArgumentException(
                "proposedAllDay is required."
        );
    }

    String normalizedTimeZone =
            normalizeAndValidateTimeZone(
                    timeZone
            );

    if (allDay) {
        return validateAllDay(
                startDate,
                endDate,
                normalizedTimeZone
        );
    }

    return validateTimed(
            startAt,
            endAt,
            normalizedTimeZone
    );
}
}