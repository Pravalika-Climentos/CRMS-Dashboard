package com.example.Call.WebSocket;

import com.example.Call.DTO.Realtime.*;
import com.example.Call.Service.CallSignalService;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import java.security.Principal;

@Controller
public class CallSignalController {
    private final CallSignalService service;
    public CallSignalController(CallSignalService service){this.service=service;}
    @MessageMapping("/calls/offer") public void offer(@Valid CallOfferRequest r, Principal p){service.offer(id(p),r);}
    @MessageMapping("/calls/answer") public void answer(@Valid CallAnswerRequest r, Principal p){service.answer(id(p),r);}
    @MessageMapping("/calls/ice") public void ice(@Valid CallIceCandidateRequest r, Principal p){service.ice(id(p),r);}
    @MessageMapping("/calls/declined") public void decline(@Valid CallControlRequest r, Principal p){service.declined(id(p),r);}
    @MessageMapping("/calls/ended") public void end(@Valid CallControlRequest r, Principal p){service.ended(id(p),r);}
    private Long id(Principal principal){if(principal==null)throw new IllegalStateException("WebSocket user is missing.");return Long.valueOf(principal.getName());}
}
