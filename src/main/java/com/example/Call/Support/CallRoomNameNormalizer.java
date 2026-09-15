package com.example.Call.Support;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class CallRoomNameNormalizer {

    private static final int MAXIMUM_LENGTH = 64;

    private static final String DEFAULT_ROOM_NAME =
            "crm-team-room";

    public String normalize(
            String value
    ) {
        String normalized =
                value == null
                        ? ""
                        : value.trim()
                               .toLowerCase(
                                   Locale.ROOT
                               );

        normalized =
                normalized.replaceAll(
                        "[^a-z0-9_-]",
                        "-"
                );

        normalized =
                normalized.replaceAll(
                        "-+",
                        "-"
                );

        normalized =
                removeEdgeSeparators(
                        normalized
                );

        if (normalized.isBlank()) {
            normalized =
                    DEFAULT_ROOM_NAME;
        }

        if (
            normalized.length() >
            MAXIMUM_LENGTH
        ) {
            normalized =
                    normalized.substring(
                            0,
                            MAXIMUM_LENGTH
                    );
        }

        return normalized;
    }

    private String removeEdgeSeparators(
            String value
    ) {
        return value
                .replaceAll(
                        "^[-_]+",
                        ""
                )
                .replaceAll(
                        "[-_]+$",
                        ""
                );
    }
}