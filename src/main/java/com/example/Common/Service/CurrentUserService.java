package com.example.Common.Service;

import com.example.Auth.Security.CrmUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.util.Arrays;

@Service
public class CurrentUserService {
    private static final String DEV_USER_HEADER = "X-Dev-User-Id";
    private final HttpServletRequest request;
    private final Environment environment;

    @Value("${chat.dev-user-override-enabled:false}") private boolean devOverrideEnabled;
    @Value("${chat.default-dev-user-id:1}") private Long defaultDevUserId;

    public CurrentUserService(HttpServletRequest request, Environment environment) {
        this.request = request;
        this.environment = environment;
    }

    public Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getPrincipal() instanceof CrmUserPrincipal principal) {
            return principal.userId();
        }
        if (devOverrideEnabled && Arrays.asList(environment.getActiveProfiles()).contains("dev")) {
            String value = request.getHeader(DEV_USER_HEADER);
            if (value == null || value.isBlank()) return defaultDevUserId;
            try {
                Long userId = Long.valueOf(value.trim());
                if (userId <= 0) throw new NumberFormatException();
                return userId;
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid X-Dev-User-Id header.");
            }
        }
        throw new IllegalStateException("Authenticated user is not available.");
    }
}
