package com.example.Call.Controller;

import com.example.Call.DTO.Request.CreateCallRequest;
import com.example.Call.DTO.Response.CallContactsResponse;
import com.example.Call.DTO.Response.CallListResponse;
import com.example.Call.DTO.Response.CallResponse;
import com.example.Call.DTO.Response.WebRtcConfigResponse;
import com.example.Call.Mapper.CallMapper;
import com.example.Call.Service.CallService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class CallController {
    private final CallService service;
    private final CallMapper mapper;

    public CallController(CallService service, CallMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping("/calls")
    public CallListResponse calls(@RequestParam(required = false) Long contactId) {
        return service.history(contactId);
    }

    @GetMapping("/calls/{callId}")
    public CallResponse call(@PathVariable String callId) {
        return service.details(callId);
    }

    @GetMapping("/calls/users")
    public CallContactsResponse users(@RequestParam(defaultValue = "") String search) {
        return service.contacts(search);
    }

    @GetMapping("/webrtc/config")
    public WebRtcConfigResponse config() {
        return service.webRtcConfig();
    }

    @GetMapping(value = "/calls/presence", params = "email")
    public Map<String, Object> presence(@RequestParam String email) {
        return service.presence(email);
    }

    @GetMapping("/calls/presence")
    public List<Map<String, Object>> presenceSnapshot() {
        return service.presenceSnapshot();
    }

    @PostMapping("/calls")
    @ResponseStatus(HttpStatus.CREATED)
    public CallResponse start(@Valid @RequestBody CreateCallRequest request) {
        return mapper.toCallResponse(service.start(request));
    }

    @PatchMapping("/calls/{callId}/answer")
    public CallResponse answer(@PathVariable String callId) {
        return mapper.toCallResponse(service.connect(callId));
    }

    @PatchMapping("/calls/{callId}/decline")
    public CallResponse decline(@PathVariable String callId) {
        return mapper.toCallResponse(service.decline(callId));
    }

    @PatchMapping("/calls/{callId}/end")
    public CallResponse end(@PathVariable String callId) {
        return mapper.toCallResponse(service.end(callId));
    }
}
