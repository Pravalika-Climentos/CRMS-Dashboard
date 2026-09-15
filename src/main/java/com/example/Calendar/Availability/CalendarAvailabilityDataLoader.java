package com.example.Calendar.Availability;

import com.example.Calendar.Entity.InvitationStatus;
import com.example.Calendar.Repository.CalendarEventRepository;
import com.example.Calendar.Repository.Projection.CalendarAvailabilityEventProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarAvailabilityDataLoader {

    private static final int MAX_USERS = 200;

    private final CalendarEventRepository eventRepository;

    public CalendarAvailabilityData load(
            Collection<Long> requestedUserIds,
            Instant from,
            Instant to,
            ZoneId searchZone,
            Instant now,
            Long excludeEventId
    ) {
        Set<Long> userIds =
                normalizeUserIds(
                        requestedUserIds
                );

        validateRange(
                from,
                to,
                searchZone,
                now
        );

        /*
         * Widen the date range by one day on either side.
         *
         * All-day events may use a timezone different from
         * the timezone selected for the availability search.
         * The exact timestamp overlap is checked later.
         */
        LocalDate queryFromDate =
                from.atZone(searchZone)
                        .toLocalDate()
                        .minusDays(1);

        LocalDate queryToDate =
                to.atZone(searchZone)
                        .toLocalDate()
                        .plusDays(1);

        List<CalendarAvailabilityEventProjection>
                organizerRows =
                    eventRepository
                        .findOrganizerAvailabilityEvents(
                            userIds,
                            from,
                            to,
                            queryFromDate,
                            queryToDate,
                            excludeEventId
                        );

        List<CalendarAvailabilityEventProjection>
                participantRows =
                    eventRepository
                        .findParticipantAvailabilityEvents(
                            userIds,
                            from,
                            to,
                            queryFromDate,
                            queryToDate,
                            now,
                            excludeEventId
                        );

        /*
         * One user/event pair should occur only once.
         * If a duplicate does occur, BUSY has priority.
         */
        Map<UserEventKey, AvailabilityBlock>
                uniqueBlocks =
                    new LinkedHashMap<>();

        for (CalendarAvailabilityEventProjection row
                : organizerRows) {
            AvailabilityBlock block =
                    toOrganizerBlock(row);

            addIfOverlapping(
                    uniqueBlocks,
                    block,
                    from,
                    to
            );
        }

        for (CalendarAvailabilityEventProjection row
                : participantRows) {
            AvailabilityBlock block =
                    toParticipantBlock(
                            row,
                            now
                    );

            if (block != null) {
                addIfOverlapping(
                        uniqueBlocks,
                        block,
                        from,
                        to
                );
            }
        }

        Map<Long, List<AvailabilityBlock>>
                blocksByUser =
                    new LinkedHashMap<>();

        /*
         * Include users with no calendar blocks.
         */
        userIds.forEach(userId ->
                blocksByUser.put(
                        userId,
                        new ArrayList<>()
                )
        );

        for (AvailabilityBlock block
                : uniqueBlocks.values()) {
            blocksByUser
                    .computeIfAbsent(
                        block.userId(),
                        ignored ->
                            new ArrayList<>()
                    )
                    .add(block);
        }

        Map<Long, List<AvailabilityBlock>>
                immutableBlocksByUser =
                    new LinkedHashMap<>();

        blocksByUser.forEach(
            (userId, blocks) -> {
                List<AvailabilityBlock> sorted =
                        blocks.stream()
                                .sorted(
                                    Comparator.comparing(
                                        AvailabilityBlock
                                            ::startAt
                                    ).thenComparing(
                                        AvailabilityBlock
                                            ::endAt
                                    )
                                )
                                .toList();

                immutableBlocksByUser.put(
                        userId,
                        sorted
                );
            }
        );

        return new CalendarAvailabilityData(
                from,
                to,
                searchZone,
                immutableBlocksByUser
        );
    }

    private AvailabilityBlock toOrganizerBlock(
            CalendarAvailabilityEventProjection row
    ) {
        EventInterval interval =
                resolveInterval(row);

        return new AvailabilityBlock(
                row.getUserId(),
                row.getEventId(),
                row.getOrganizerId(),
                row.getTitle(),
                row.getVisibility(),
                AvailabilityBlockType.BUSY,
                Boolean.TRUE.equals(
                        row.getAllDay()
                ),
                interval.start(),
                interval.end(),
                row.getStartDate(),
                row.getEndDate(),
                row.getTimeZone()
        );
    }

    private AvailabilityBlock toParticipantBlock(
            CalendarAvailabilityEventProjection row,
            Instant now
    ) {
        InvitationStatus status =
                row.getParticipationStatus();

        AvailabilityBlockType type;

        if (status == InvitationStatus.ACCEPTED) {
            type = AvailabilityBlockType.BUSY;

        } else if (
            status == InvitationStatus.PENDING
            && row.getInvitationExpiresAt() != null
            && row.getInvitationExpiresAt()
                    .isAfter(now)
        ) {
            type = AvailabilityBlockType.TENTATIVE;

        } else {
            /*
             * Defensive check. The repository already filters
             * all other participant statuses.
             */
            return null;
        }

        EventInterval interval =
                resolveInterval(row);

        return new AvailabilityBlock(
                row.getUserId(),
                row.getEventId(),
                row.getOrganizerId(),
                row.getTitle(),
                row.getVisibility(),
                type,
                Boolean.TRUE.equals(
                        row.getAllDay()
                ),
                interval.start(),
                interval.end(),
                row.getStartDate(),
                row.getEndDate(),
                row.getTimeZone()
        );
    }

    private EventInterval resolveInterval(
            CalendarAvailabilityEventProjection row
    ) {
        if (!Boolean.TRUE.equals(
                row.getAllDay()
        )) {
            if (row.getStartAt() == null
                    || row.getEndAt() == null) {
                throw new IllegalStateException(
                        "Timed calendar event "
                        + row.getEventId()
                        + " has an invalid schedule."
                );
            }

            return new EventInterval(
                    row.getStartAt(),
                    row.getEndAt()
            );
        }

        if (row.getStartDate() == null
                || row.getEndDate() == null) {
            throw new IllegalStateException(
                    "All-day calendar event "
                    + row.getEventId()
                    + " has an invalid schedule."
            );
        }

        ZoneId eventZone =
                ZoneId.of(
                    row.getTimeZone()
                );

        return new EventInterval(
                row.getStartDate()
                        .atStartOfDay(eventZone)
                        .toInstant(),

                row.getEndDate()
                        .atStartOfDay(eventZone)
                        .toInstant()
        );
    }

    private void addIfOverlapping(
            Map<UserEventKey, AvailabilityBlock>
                    uniqueBlocks,
            AvailabilityBlock candidate,
            Instant from,
            Instant to
    ) {
        /*
         * Exact half-open interval overlap:
         *
         * event start < requested end
         * event end   > requested start
         */
        if (!candidate.startAt().isBefore(to)
                || !candidate.endAt()
                    .isAfter(from)) {
            return;
        }

        UserEventKey key =
                new UserEventKey(
                        candidate.userId(),
                        candidate.eventId()
                );

        uniqueBlocks.merge(
                key,
                candidate,
                this::chooseStrongerBlock
        );
    }

    private AvailabilityBlock chooseStrongerBlock(
            AvailabilityBlock existing,
            AvailabilityBlock candidate
    ) {
        if (existing.isBusy()) {
            return existing;
        }

        if (candidate.isBusy()) {
            return candidate;
        }

        return existing;
    }

    private Set<Long> normalizeUserIds(
            Collection<Long> requestedUserIds
    ) {
        if (requestedUserIds == null
                || requestedUserIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one user is required."
            );
        }

        Set<Long> userIds =
                new LinkedHashSet<>();

        for (Long userId : requestedUserIds) {
            if (userId == null || userId <= 0) {
                throw new IllegalArgumentException(
                        "User IDs must be positive."
                );
            }

            userIds.add(userId);
        }

        if (userIds.size() > MAX_USERS) {
            throw new IllegalArgumentException(
                    "A maximum of 200 users can be checked."
            );
        }

        return userIds;
    }

    private void validateRange(
            Instant from,
            Instant to,
            ZoneId searchZone,
            Instant now
    ) {
        if (from == null || to == null) {
            throw new IllegalArgumentException(
                    "Availability range is required."
            );
        }

        if (!to.isAfter(from)) {
            throw new IllegalArgumentException(
                    "Availability range end must be after start."
            );
        }

        if (searchZone == null) {
            throw new IllegalArgumentException(
                    "Availability timezone is required."
            );
        }

        if (now == null) {
            throw new IllegalArgumentException(
                    "Current time is required."
            );
        }
    }

    private record UserEventKey(
            Long userId,
            Long eventId
    ) {
    }

    private record EventInterval(
            Instant start,
            Instant end
    ) {
    }
}