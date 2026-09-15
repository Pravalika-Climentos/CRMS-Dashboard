package com.example.Call.WebSocket;

import com.example.Call.DTO.Realtime.*;
import com.example.Call.Support.CallRoomNameNormalizer;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import java.security.Principal;
import java.time.Instant;

@Controller
public class CallRoomRealtimeController {
    private final SimpMessagingTemplate messaging; private final CallRoomNameNormalizer names;
    public CallRoomRealtimeController(SimpMessagingTemplate messaging,CallRoomNameNormalizer names){this.messaging=messaging;this.names=names;}

    @MessageMapping("/call-rooms/chat")
    public void chat(@Valid TeamRoomChatRequest request, Principal principal){
        String room=names.normalize(request.room());
        TeamRoomChatEvent payload=new TeamRoomChatEvent(room,Long.valueOf(principal.getName()),principal.getName(),request.text(),Instant.now());
        messaging.convertAndSend("/topic/call-rooms/"+room,new CallRealtimeEvent("team:chat",payload));
    }

    @MessageMapping("/call-rooms/notes")
    public void notes(TeamRoomNotesEvent request,Principal principal){
        String room=names.normalize(request.room());
        TeamRoomNotesEvent payload=new TeamRoomNotesEvent(room,Long.valueOf(principal.getName()),request.name(),request.notes(),Instant.now());
        messaging.convertAndSend("/topic/call-rooms/"+room,new CallRealtimeEvent("team:notes",payload));
    }
}
