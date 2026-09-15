package com.example.CRM.DTO.Response;

import java.util.List;

public record TeamMembershipResultResponse(
        String teamId,
        long memberCount,
        long version,
        List<String> addedUserIds,
        List<String> alreadyMemberUserIds,
        List<String> removedUserIds
) {

    public TeamMembershipResultResponse {
        addedUserIds = safeCopy(addedUserIds);
        alreadyMemberUserIds =
                safeCopy(alreadyMemberUserIds);
        removedUserIds =
                safeCopy(removedUserIds);
    }

    private static List<String> safeCopy(
            List<String> values
    ) {
        return values == null
                ? List.of()
                : List.copyOf(values);
    }
}