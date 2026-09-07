package com.example.Calendar.Availability;

import com.example.Calendar.DTO.Response.AvailabilityDayResponse;
import com.example.Calendar.DTO.Response.SuggestedSlotResponse;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
public class AvailabilitySuggestionEngine {

    private static final int SLOT_INTERVAL_MINUTES = 15;

    public List<AvailabilityDayResponse> recommend(
            CalendarAvailabilityData availabilityData,
            Collection<Long> requestedUserIds,
            LocalDate fromDate,
            int workingDays,
            int durationMinutes,
            LocalTime workDayStart,
            LocalTime workDayEnd,
            int maximumSlotsPerDay,
            Instant now
    ) {
        validate(
                availabilityData,
                requestedUserIds,
                fromDate,
                workingDays,
                durationMinutes,
                workDayStart,
                workDayEnd,
                maximumSlotsPerDay,
                now
        );

        Set<Long> userIds = normalizeUserIds(requestedUserIds);

        List<AvailabilityDayResponse> results = new ArrayList<>();

        LocalDate currentDate = fromDate;
        int processedWorkingDays = 0;

        while (processedWorkingDays < workingDays) {
            if (!isWorkingDay(currentDate)) {
                currentDate = currentDate.plusDays(1);
                continue;
            }

            results.add(
                    recommendForDay(
                            availabilityData,
                            userIds,
                            currentDate,
                            durationMinutes,
                            workDayStart,
                            workDayEnd,
                            maximumSlotsPerDay,
                            now
                    )
            );

            processedWorkingDays++;
            currentDate = currentDate.plusDays(1);
        }

        return List.copyOf(results);
    }

    private AvailabilityDayResponse recommendForDay(
            CalendarAvailabilityData availabilityData,
            Set<Long> userIds,
            LocalDate date,
            int durationMinutes,
            LocalTime workDayStart,
            LocalTime workDayEnd,
            int maximumSlotsPerDay,
            Instant now
    ) {
        ZonedDateTime dayStart =
                date.atTime(workDayStart)
                    .atZone(availabilityData.zone());

        ZonedDateTime dayEnd =
                date.atTime(workDayEnd)
                    .atZone(availabilityData.zone());

        ZonedDateTime effectiveStart = dayStart;

        ZonedDateTime nowInSearchZone =
                now.atZone(availabilityData.zone());

        if (date.equals(nowInSearchZone.toLocalDate())) {
            ZonedDateTime roundedNow =
                    roundUpToNextInterval(
                            nowInSearchZone,
                            SLOT_INTERVAL_MINUTES
                    );

            if (roundedNow.isAfter(effectiveStart)) {
                effectiveStart = roundedNow;
            }
        }

        if (effectiveStart.isBefore(dayStart)) {
            effectiveStart = dayStart;
        }

        List<SuggestedSlotResponse> selectedSlots;

        if (!effectiveStart.isBefore(dayEnd)) {
            selectedSlots = List.of();
        } else {
            List<SlotCandidate> candidates =
                    generateCandidates(
                            availabilityData,
                            userIds,
                            effectiveStart,
                            dayEnd,
                            durationMinutes
                    );

            selectedSlots =
                    rankAndSelect(
                            candidates,
                            dayStart,
                            dayEnd,
                            maximumSlotsPerDay
                    );
        }

      return new AvailabilityDayResponse(
        date,
        selectedSlots
       );
    }

    private List<SlotCandidate> generateCandidates(
            CalendarAvailabilityData availabilityData,
            Set<Long> userIds,
            ZonedDateTime effectiveStart,
            ZonedDateTime dayEnd,
            int durationMinutes
    ) {
        List<AvailabilityBlock> busyBlocks =
                getBlocks(
                        availabilityData,
                        userIds,
                        AvailabilityBlockType.BUSY
                );

        List<AvailabilityBlock> tentativeBlocks =
                getBlocks(
                        availabilityData,
                        userIds,
                        AvailabilityBlockType.TENTATIVE
                );

        List<SlotCandidate> candidates = new ArrayList<>();

        ZonedDateTime candidateStart = effectiveStart;

        while (true) {
            ZonedDateTime candidateEnd =
                    candidateStart.plusMinutes(durationMinutes);

            if (candidateEnd.isAfter(dayEnd)) {
                break;
            }

            Instant candidateStartInstant =
                    candidateStart.toInstant();

            Instant candidateEndInstant =
                    candidateEnd.toInstant();

            boolean hasBusyConflict =
                    busyBlocks.stream()
                              .anyMatch(block ->
                                  overlaps(
                                      candidateStartInstant,
                                      candidateEndInstant,
                                      block.startAt(),
                                      block.endAt()
                                  )
                              );

            if (!hasBusyConflict) {
                int tentativeConflictCount =
                        countTentativeConflicts(
                                tentativeBlocks,
                                candidateStartInstant,
                                candidateEndInstant
                        );

                candidates.add(
                        new SlotCandidate(
                                candidateStartInstant,
                                candidateEndInstant,
                                tentativeConflictCount
                        )
                );
            }

            candidateStart =
                    candidateStart.plusMinutes(
                            SLOT_INTERVAL_MINUTES
                    );
        }

        return candidates;
    }

    private List<AvailabilityBlock> getBlocks(
            CalendarAvailabilityData availabilityData,
            Set<Long> userIds,
            AvailabilityBlockType type
    ) {
        return userIds.stream()
                      .flatMap(userId ->
                          availabilityData
                              .blocksForUser(userId)
                              .stream()
                      )
                      .filter(block -> block.type() == type)
                      .toList();
    }

    private int countTentativeConflicts(
            List<AvailabilityBlock> tentativeBlocks,
            Instant candidateStart,
            Instant candidateEnd
    ) {
        Set<TentativeConflictKey> conflicts = new HashSet<>();

        for (AvailabilityBlock block : tentativeBlocks) {
            if (overlaps(
                    candidateStart,
                    candidateEnd,
                    block.startAt(),
                    block.endAt()
            )) {
                conflicts.add(
                        new TentativeConflictKey(
                                block.userId(),
                                block.eventId()
                        )
                );
            }
        }

        return conflicts.size();
    }

    private List<SuggestedSlotResponse> rankAndSelect(
            List<SlotCandidate> candidates,
            ZonedDateTime dayStart,
            ZonedDateTime dayEnd,
            int maximumSlotsPerDay
    ) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        Comparator<SlotCandidate> ranking =
                Comparator
                    .comparingInt(
                        SlotCandidate::tentativeConflictCount
                    )
                    .thenComparing(
                        SlotCandidate::startAt
                    );

        List<SlotCandidate> selected = new ArrayList<>();

        long workDayMinutes =
                Duration.between(
                        dayStart,
                        dayEnd
                ).toMinutes();

        long firstBoundaryMinutes =
                workDayMinutes / 3;

        long secondBoundaryMinutes =
                (workDayMinutes * 2) / 3;

        Instant firstBoundary =
                dayStart
                    .plusMinutes(firstBoundaryMinutes)
                    .toInstant();

        Instant secondBoundary =
                dayStart
                    .plusMinutes(secondBoundaryMinutes)
                    .toInstant();

        /*
         * First choose one strong candidate from each part of the day:
         * morning, midday and afternoon.
         */
        addBestCandidate(
                selected,
                candidates.stream()
                          .filter(candidate ->
                              candidate.startAt()
                                       .isBefore(firstBoundary)
                          )
                          .min(ranking)
                          .orElse(null),
                maximumSlotsPerDay
        );

        addBestCandidate(
                selected,
                candidates.stream()
                          .filter(candidate ->
                              !candidate.startAt()
                                        .isBefore(firstBoundary)
                              &&
                              candidate.startAt()
                                       .isBefore(secondBoundary)
                          )
                          .min(ranking)
                          .orElse(null),
                maximumSlotsPerDay
        );

        addBestCandidate(
                selected,
                candidates.stream()
                          .filter(candidate ->
                              !candidate.startAt()
                                        .isBefore(secondBoundary)
                          )
                          .min(ranking)
                          .orElse(null),
                maximumSlotsPerDay
        );

        /*
         * If a time band had no valid slot, fill the remaining positions
         * with the best unselected candidates from the entire day.
         */
        if (selected.size() < maximumSlotsPerDay) {
            candidates.stream()
                      .sorted(ranking)
                      .filter(candidate ->
                          !selected.contains(candidate)
                      )
                      .limit(
                          maximumSlotsPerDay - selected.size()
                      )
                      .forEach(selected::add);
        }

        return selected.stream()
                       .sorted(
                           Comparator.comparing(
                               SlotCandidate::startAt
                           )
                       )
                       .map(this::toResponse)
                       .toList();
    }

    private void addBestCandidate(
            List<SlotCandidate> selected,
            SlotCandidate candidate,
            int maximumSlotsPerDay
    ) {
        if (candidate == null) {
            return;
        }

        if (selected.size() >= maximumSlotsPerDay) {
            return;
        }

        if (!selected.contains(candidate)) {
            selected.add(candidate);
        }
    }

    private SuggestedSlotResponse toResponse(
        SlotCandidate candidate
) {
    return new SuggestedSlotResponse(
            candidate.startAt(),
            candidate.endAt(),
            Math.toIntExact(
                    Duration.between(
                            candidate.startAt(),
                            candidate.endAt()
                    ).toMinutes()
            ),
            candidate.tentativeConflictCount()
    );
}

    private ZonedDateTime roundUpToNextInterval(
            ZonedDateTime value,
            int intervalMinutes
    ) {
        ZonedDateTime minuteValue =
                value.truncatedTo(ChronoUnit.MINUTES);

        boolean alreadyOnBoundary =
                value.getSecond() == 0
                && value.getNano() == 0
                && minuteValue.getMinute() % intervalMinutes == 0;

        if (alreadyOnBoundary) {
            return minuteValue;
        }

        int minutesToAdd =
                intervalMinutes
                - (minuteValue.getMinute() % intervalMinutes);

        return minuteValue.plusMinutes(minutesToAdd);
    }

    private boolean overlaps(
            Instant firstStart,
            Instant firstEnd,
            Instant secondStart,
            Instant secondEnd
    ) {
        return firstStart.isBefore(secondEnd)
                && firstEnd.isAfter(secondStart);
    }

    private Set<Long> normalizeUserIds(
            Collection<Long> requestedUserIds
    ) {
        Set<Long> normalized = new HashSet<>();

        for (Long userId : requestedUserIds) {
            if (userId != null && userId > 0) {
                normalized.add(userId);
            }
        }

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one valid user is required."
            );
        }

        return Set.copyOf(normalized);
    }

    private boolean isWorkingDay(LocalDate date) {
        return date.getDayOfWeek() != DayOfWeek.SATURDAY
                && date.getDayOfWeek() != DayOfWeek.SUNDAY;
    }

    private void validate(
            CalendarAvailabilityData availabilityData,
            Collection<Long> requestedUserIds,
            LocalDate fromDate,
            int workingDays,
            int durationMinutes,
            LocalTime workDayStart,
            LocalTime workDayEnd,
            int maximumSlotsPerDay,
            Instant now
    ) {
        Objects.requireNonNull(
                availabilityData,
                "Availability data is required."
        );
        Objects.requireNonNull(
                requestedUserIds,
                "User IDs are required."
        );
        Objects.requireNonNull(
                fromDate,
                "From date is required."
        );
        Objects.requireNonNull(
                workDayStart,
                "Workday start is required."
        );
        Objects.requireNonNull(
                workDayEnd,
                "Workday end is required."
        );
        Objects.requireNonNull(
                now,
                "Current time is required."
        );

        if (workingDays < 1 || workingDays > 31) {
            throw new IllegalArgumentException(
                    "Working days must be between 1 and 31."
            );
        }

        if (durationMinutes < 15 || durationMinutes > 480) {
            throw new IllegalArgumentException(
                    "Duration must be between 15 and 480 minutes."
            );
        }

        if (durationMinutes % SLOT_INTERVAL_MINUTES != 0) {
            throw new IllegalArgumentException(
                    "Duration must use 15-minute increments."
            );
        }

        if (!workDayStart.isBefore(workDayEnd)) {
            throw new IllegalArgumentException(
                    "Workday start must be before workday end."
            );
        }

        long workDayMinutes =
                Duration.between(
                        workDayStart,
                        workDayEnd
                ).toMinutes();

        if (durationMinutes > workDayMinutes) {
            throw new IllegalArgumentException(
                    "Duration cannot exceed the workday."
            );
        }

        if (maximumSlotsPerDay < 1
                || maximumSlotsPerDay > 10) {
            throw new IllegalArgumentException(
                    "Maximum slots per day must be between 1 and 10."
            );
        }
    }

    private record SlotCandidate(
            Instant startAt,
            Instant endAt,
            int tentativeConflictCount
    ) {
    }

    private record TentativeConflictKey(
            Long userId,
            Long eventId
    ) {
    }
}