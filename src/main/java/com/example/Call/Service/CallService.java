package com.example.Call.Service;

import com.example.Call.Config.CallProperties;
import com.example.Call.DTO.Request.CreateCallRequest;
import com.example.Call.DTO.Response.*;
import com.example.Call.Entity.*;
import com.example.Call.Mapper.CallMapper;
import com.example.Call.Repository.CallSessionRepository;
import com.example.CRM.Entity.User;
import com.example.CRM.Repository.UserRepository;
import com.example.Common.Exception.*;
import com.example.Common.Service.CurrentUserService;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
public class CallService {
    private static final List<CallStatus> ACTIVE = List.of(CallStatus.RINGING, CallStatus.CONNECTED);
    private final CallSessionRepository calls;
    private final UserRepository users;
    private final CurrentUserService currentUser;
    private final CallMapper mapper;
    private final CallProperties properties;
    private final CallPresenceService presence;
    private final Clock clock;

    public CallService(CallSessionRepository calls, UserRepository users, CurrentUserService currentUser,
                       CallMapper mapper, CallProperties properties, CallPresenceService presence, Clock clock) {
        this.calls = calls; this.users = users; this.currentUser = currentUser;
        this.mapper = mapper; this.properties = properties; this.presence = presence; this.clock = clock;
    }

    @Transactional
    public CallSession start(CreateCallRequest request) {
        Long callerId = currentUser.getCurrentUserId();
        User caller = activeUser(callerId);
        User callee = users.findByEmailIgnoreCase(request.recipientEmail().trim())
                .filter(u -> Boolean.TRUE.equals(u.getActive()))
                .orElseThrow(() -> new ResourceNotFoundException("Call recipient was not found."));
        if (callerId.equals(callee.getUserId())) throw new IllegalArgumentException("You cannot call yourself.");
        if (calls.existsById(request.callId())) return calls.findById(request.callId()).orElseThrow();
        if (calls.existsActiveCallForUser(callerId, ACTIVE) || calls.existsActiveCallForUser(callee.getUserId(), ACTIVE))
            throw new ConflictException("USER_BUSY", "The caller or recipient is already in another call.");
        CallSession call = new CallSession();
        call.setCallId(request.callId()); call.setCaller(caller); call.setCallee(callee);
        call.setCallType(CallType.valueOf(request.type().trim().toUpperCase(Locale.ROOT)));
        call.setStatus(CallStatus.RINGING); call.setStartedAt(clock.instant()); call.setDurationSeconds(0L);
        return calls.save(call);
    }

    @Transactional
    public CallSession connect(String callId) {
        CallSession call = locked(callId); Long userId = currentUser.getCurrentUserId();
        if (!call.getCallee().getUserId().equals(userId)) throw new ForbiddenOperationException("Only the recipient can answer this call.");
        requireStatus(call, CallStatus.RINGING); call.setStatus(CallStatus.CONNECTED); call.setAnsweredAt(clock.instant());
        return call;
    }

    @Transactional
    public CallSession decline(String callId) {
        CallSession call = locked(callId); Long userId = currentUser.getCurrentUserId();
        if (!call.getCallee().getUserId().equals(userId)) throw new ForbiddenOperationException("Only the recipient can decline this call.");
        requireStatus(call, CallStatus.RINGING); finish(call, CallStatus.DECLINED, "RECIPIENT_DECLINED", userId);
        return call;
    }

    @Transactional
    public CallSession end(String callId) {
        CallSession call = locked(callId); Long userId = currentUser.getCurrentUserId();
        requireParticipant(call, userId);
        if (!ACTIVE.contains(call.getStatus())) return call;
        finish(call, call.getStatus() == CallStatus.CONNECTED ? CallStatus.COMPLETED : CallStatus.CANCELLED,
                call.getStatus() == CallStatus.CONNECTED ? "CALL_ENDED" : "CALL_CANCELLED", userId);
        return call;
    }

    @Transactional(readOnly = true)
    public CallListResponse history(Long contactId) {
        List<CallResponse> result = calls.findCallHistory(currentUser.getCurrentUserId(), contactId, PageRequest.of(0, 100))
                .getContent().stream().map(mapper::toCallResponse).toList();
        return new CallListResponse(result);
    }

    @Transactional(readOnly = true)
    public CallResponse details(String callId) {
        return mapper.toCallResponse(calls.findVisibleCall(callId, currentUser.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Call was not found.")));
    }

    @Transactional(readOnly = true)
    public CallContactsResponse contacts(String search) {
        String q = search == null ? "" : search.trim();
        var page = users.searchActiveCalendarUsers(q, currentUser.getCurrentUserId(), PageRequest.of(0, 100));
        return new CallContactsResponse(page.getContent().stream().map(u -> new CallContactResponse(
                u.getUserId(), u.getEmail(), u.getPhone(), u.getFullName(),
                u.getAvatar() == null ? "assets/img/profiles/avatar-01.jpg" : u.getAvatar(),
                "Chat", "", null, presence.isOnline(u.getUserId()) ? "online" : "offline")).toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> presence(String email) {
        return presence.byEmail(email);
    }

    public List<Map<String, Object>> presenceSnapshot() {
        return presence.snapshot();
    }

    public WebRtcConfigResponse webRtcConfig() {
        List<IceServerResponse> ice = new ArrayList<>();
        properties.getWebRtc().getStunUrls().stream().filter(s -> s != null && !s.isBlank())
                .forEach(url -> ice.add(new IceServerResponse(List.of(url), null, null)));
        if (properties.getWebRtc().hasCompleteTurnConfiguration()) {
            List<String> urls = properties.getWebRtc().getTurnUrls().stream().filter(s -> s != null && !s.isBlank()).toList();
            if (!urls.isEmpty()) ice.add(new IceServerResponse(urls, properties.getWebRtc().getTurnUsername(), properties.getWebRtc().getTurnCredential()));
        }
        return new WebRtcConfigResponse(ice);
    }

    @Scheduled(fixedDelayString = "${calls.missed-call-scan-delay:15s}")
    @Transactional
    public void markMissedCalls() {
        Instant now = clock.instant();
        calls.markExpiredRingingCallsAsMissed(CallStatus.RINGING, CallStatus.MISSED,
                now.minus(properties.getRingTimeout()), now);
    }

    private User activeUser(Long id) { return users.findById(id).filter(u -> Boolean.TRUE.equals(u.getActive()))
            .orElseThrow(() -> new ResourceNotFoundException("User was not found.")); }
    private CallSession locked(String id) { return calls.findByCallIdForUpdate(id)
            .orElseThrow(() -> new ResourceNotFoundException("Call was not found.")); }
    private void requireParticipant(CallSession c, Long id) { if (!c.getCaller().getUserId().equals(id) && !c.getCallee().getUserId().equals(id))
        throw new ForbiddenOperationException("You are not a participant in this call."); }
    private void requireStatus(CallSession c, CallStatus status) { if (c.getStatus() != status)
        throw new ConflictException("CALL_STATE_CHANGED", "The call is no longer " + status.name().toLowerCase(Locale.ROOT) + "."); }
    private void finish(CallSession c, CallStatus status, String reason, Long endedBy) {
        Instant now = clock.instant(); c.setStatus(status); c.setEndedAt(now); c.setEndReason(reason); c.setEndedBy(activeUser(endedBy));
        c.setDurationSeconds(c.getAnsweredAt() == null ? 0L : Math.max(0, Duration.between(c.getAnsweredAt(), now).toSeconds()));
    }
}
