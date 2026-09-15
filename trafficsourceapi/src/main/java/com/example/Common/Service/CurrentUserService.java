package com.example.Common.Service;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    private static final String DEV_USER_HEADER =
            "X-Dev-User-Id";

    private final HttpServletRequest request;

    @Value("${chat.dev-user-override-enabled:false}")
    private boolean devOverrideEnabled;

    @Value("${chat.default-dev-user-id:1}")
    private Long defaultDevUserId;

    
    public CurrentUserService(HttpServletRequest request)
    {
        this.request = request;
    }

    public Long getCurrentUserId() {

        if (!devOverrideEnabled) {
            return defaultDevUserId;
        }

        String header =
                request.getHeader(
                        DEV_USER_HEADER
                );

        if (header == null ||
            header.isBlank()) {

            return defaultDevUserId;
        }

        try {
            Long userId =
                    Long.valueOf(
                            header.trim()
                    );

            if (userId <= 0) {
                throw new IllegalArgumentException(
                        "Development user ID must be positive."
                );
            }

            return userId;

        } catch (NumberFormatException exception) {

            throw new IllegalArgumentException(
                    "Invalid X-Dev-User-Id header."
            );
        }
    }
}