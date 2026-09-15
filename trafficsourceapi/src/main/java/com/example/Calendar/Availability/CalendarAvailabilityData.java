package com.example.Calendar.Availability;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

public record CalendarAvailabilityData(

        Instant from,

        Instant to,

        ZoneId zone,

        Map<Long, List<AvailabilityBlock>>
                blocksByUser

) {
    public CalendarAvailabilityData {
        blocksByUser =
                blocksByUser == null
                        ? Map.of()
                        : Map.copyOf(
                                blocksByUser
                        );
    }

    public List<AvailabilityBlock> blocksForUser(
            Long userId
    ) {
        return blocksByUser.getOrDefault(
                userId,
                List.of()
        );
    }

    public List<AvailabilityBlock> busyBlocksForUser(
            Long userId
    ) {
        return blocksForUser(userId)
                .stream()
                .filter(
                    AvailabilityBlock::isBusy
                )
                .toList();
    }

    public List<AvailabilityBlock>
    tentativeBlocksForUser(
            Long userId
    ) {
        return blocksForUser(userId)
                .stream()
                .filter(
                    AvailabilityBlock::isTentative
                )
                .toList();
    }
}