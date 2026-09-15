package com.example.Call.Controller;

import com.example.Call.DTO.Request.LiveKitTokenRequest;
import com.example.Call.DTO.Request.UpdateTeamRoomNotesRequest;
import com.example.Call.DTO.Response.CallRoomParticipantResponse;
import com.example.Call.DTO.Response.LiveKitTokenResponse;
import com.example.Call.DTO.Response.TeamRoomNotesResponse;
import com.example.Call.Service.CallRoomService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CallRoomController {
    private final CallRoomService service;

    public CallRoomController(CallRoomService service) {
        this.service = service;
    }

    @PostMapping("/livekit/token")
    public LiveKitTokenResponse token(@Valid @RequestBody(required = false) LiveKitTokenRequest request) {
        return service.joinAndIssueToken(request);
    }

    @PostMapping("/team-rooms/{room}/leave")
    public ResponseEntity<Void> leave(@PathVariable String room) {
        service.leave(room);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/call-rooms/{roomId}/end")
    public ResponseEntity<Void> end(@PathVariable String roomId,
                                    @RequestParam(defaultValue = "false") boolean cancel) {
        service.end(roomId, cancel);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/team-rooms/{room}/notes")
    public TeamRoomNotesResponse notes(@PathVariable String room) {
        return service.notes(room);
    }

    @PutMapping("/team-rooms/{room}/notes")
    public TeamRoomNotesResponse notes(@PathVariable String room,
                                       @Valid @RequestBody UpdateTeamRoomNotesRequest request) {
        return service.updateNotes(room, request);
    }

    @GetMapping("/call-rooms/{roomId}/participants")
    public List<CallRoomParticipantResponse> participants(@PathVariable String roomId) {
        return service.activeParticipants(roomId);
    }
}
