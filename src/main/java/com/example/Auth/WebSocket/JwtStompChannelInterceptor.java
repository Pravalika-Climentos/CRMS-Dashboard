package com.example.Auth.WebSocket;

import com.example.Auth.Security.CrmUserPrincipal;
import com.example.Auth.Security.JwtService;
import com.example.CRM.Entity.User;
import com.example.CRM.Repository.UserRepository;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class JwtStompChannelInterceptor implements ChannelInterceptor {
    private final JwtService jwtService;
    private final UserRepository users;

    public JwtStompChannelInterceptor(JwtService jwtService, UserRepository users) {
        this.jwtService = jwtService;
        this.users = users;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String value = accessor.getFirstNativeHeader("Authorization");
            if (value == null || !value.startsWith("Bearer ")) {
                throw new MessagingException("WebSocket authentication is required.");
            }
            Long userId = jwtService.validateAndGetUserId(value.substring(7));
            User user = users.findById(userId)
                    .filter(item -> Boolean.TRUE.equals(item.getActive()))
                    .filter(item -> !Boolean.TRUE.equals(item.getAccountLocked()))
                    .orElseThrow(() -> new MessagingException("WebSocket user is unavailable."));
            var principal = CrmUserPrincipal.from(user);
            accessor.setUser(new UsernamePasswordAuthenticationToken(principal, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()))));
        }
        return message;
    }
}
