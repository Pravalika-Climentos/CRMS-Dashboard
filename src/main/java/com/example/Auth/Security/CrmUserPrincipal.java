package com.example.Auth.Security;

import com.example.CRM.Entity.User;
import java.security.Principal;

public record CrmUserPrincipal(Long userId, String email, String role) implements Principal {
    public static CrmUserPrincipal from(User user) {
        return new CrmUserPrincipal(user.getUserId(), user.getEmail(), user.getRole());
    }

    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}
