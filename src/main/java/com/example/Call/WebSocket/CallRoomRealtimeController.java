package com.example.Call.WebSocket;

import com.example.Call.DTO.Realtime.CallRealtimeEvent;
import com.example.Call.DTO.Realtime.TeamRoomChatEvent;
import com.example.Call.DTO.Realtime.TeamRoomChatRequest;
import com.example.Call.DTO.Realtime.TeamRoomNotesEvent;
import com.example.Call.Support.CallRoomNameNormalizer;
import com.example.CRM.Entity.User;
import com.example.CRM.Repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Instant;

@Controller
public class CallRoomRealtimeController {

    private final SimpMessagingTemplate messaging;
    private final CallRoomNameNormalizer names;
    private final UserRepository userRepository;

    public CallRoomRealtimeController(
            SimpMessagingTemplate messaging,
            CallRoomNameNormalizer names,
            UserRepository userRepository
    ) {
        this.messaging = messaging;
        this.names = names;
        this.userRepository = userRepository;
    }

    @MessageMapping("/call-rooms/chat")
    public void chat(
            @Valid TeamRoomChatRequest request,
            Principal principal
    ) {
        Long userId = Long.valueOf(principal.getName());

        User sender = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Authenticated user was not found."
                        )
                );

        String room = names.normalize(request.room());

        TeamRoomChatEvent payload = new TeamRoomChatEvent(
                room,
                userId,
                sender.getFullName(),
                request.text(),
                Instant.now()
        );

        messaging.convertAndSend(
                "/topic/call-rooms/" + room,
                new CallRealtimeEvent("team:chat", payload)
        );
    }

    @MessageMapping("/call-rooms/notes")
    public void notes(
            TeamRoomNotesEvent request,
            Principal principal
    ) {
        Long userId = Long.valueOf(principal.getName());

        User sender = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Authenticated user was not found."
                        )
                );

        String room = names.normalize(request.room());

        TeamRoomNotesEvent payload = new TeamRoomNotesEvent(
                room,
                userId,
                sender.getFullName(),
                request.notes(),
                Instant.now()
        );

        messaging.convertAndSend(
                "/topic/call-rooms/" + room,
                new CallRealtimeEvent("team:notes", payload)
        );
    }
}