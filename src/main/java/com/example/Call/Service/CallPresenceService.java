package com.example.Call.Service;

import com.example.CRM.Entity.User;
import com.example.CRM.Repository.UserRepository;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CallPresenceService {
    private final UserRepository users;
    private final SimpMessagingTemplate messaging;
    private final Map<Long, Set<String>> sessionsByUser = new ConcurrentHashMap<>();
    private final Map<String, Long> userBySession = new ConcurrentHashMap<>();

    public CallPresenceService(UserRepository users, SimpMessagingTemplate messaging) {
        this.users = users;
        this.messaging = messaging;
    }

    @EventListener
    public void connected(SessionConnectedEvent event) {
        StompHeaderAccessor headers = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = headers.getUser() != null ? headers.getUser() : event.getUser();
        String sessionId = headers.getSessionId();
        if (principal == null || sessionId == null) return;
        Long userId = Long.valueOf(principal.getName());
        userBySession.put(sessionId, userId);
        sessionsByUser.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(sessionId);
        publish(userId, true);
    }

    @EventListener
    public void disconnected(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        Long userId = userBySession.remove(sessionId);
        if (userId == null && event.getUser() != null) userId = Long.valueOf(event.getUser().getName());
        if (userId == null) return;
        Set<String> sessions = sessionsByUser.get(userId);
        if (sessions != null) {
            sessions.remove(sessionId);
            if (sessions.isEmpty()) sessionsByUser.remove(userId);
        }
        publish(userId, isOnline(userId));
    }

    public boolean isOnline(Long userId) {
        Set<String> sessions = sessionsByUser.get(userId);
        return sessions != null && !sessions.isEmpty();
    }

    public Map<String, Object> byEmail(String email) {
        User user = users.findByEmailIgnoreCase(email == null ? "" : email.trim())
                .orElse(null);
        if (user == null) return Map.of("online", false, "status", "offline");
        return state(user.getUserId());
    }

    public List<Map<String, Object>> snapshot() {
        return sessionsByUser.keySet().stream().sorted().map(this::state).toList();
    }

    private Map<String, Object> state(Long userId) {
        boolean online = isOnline(userId);
        return Map.of("userId", String.valueOf(userId), "online", online,
                "status", online ? "online" : "offline");
    }

    private void publish(Long userId, boolean online) {
        Object event = Map.of("type", "presence:update", "payload",
                Map.of("userId", String.valueOf(userId), "online", online,
                        "status", online ? "online" : "offline"));
        messaging.convertAndSend("/topic/calls/presence", event);
    }
}
