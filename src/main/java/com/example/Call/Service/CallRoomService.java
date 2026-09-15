package com.example.Call.Service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.example.Call.Config.CallProperties;
import com.example.Call.DTO.Request.*;
import com.example.Call.DTO.Response.*;
import com.example.Call.Entity.*;
import com.example.Call.Mapper.CallMapper;
import com.example.Call.Repository.*;
import com.example.Call.Support.CallRoomNameNormalizer;
import com.example.CRM.Entity.*;
import com.example.CRM.Repository.*;
import com.example.Common.Exception.*;
import com.example.Common.Service.CurrentUserService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
public class CallRoomService {
    private static final List<CallRoomStatus> USABLE = List.of(CallRoomStatus.OPEN, CallRoomStatus.ACTIVE);
    private final CallRoomRepository rooms;
    private final CallRoomParticipantRepository participants;
    private final UserRepository users;
    private final TeamRepository teams;
    private final TeamMemberRepository teamMembers;
    private final CurrentUserService currentUser;
    private final CallProperties properties;
    private final CallRoomNameNormalizer names;
    private final CallMapper mapper;
    private final Clock clock;

    public CallRoomService(CallRoomRepository rooms, CallRoomParticipantRepository participants,
                           UserRepository users, TeamRepository teams, TeamMemberRepository teamMembers,
                           CurrentUserService currentUser, CallProperties properties,
                           CallRoomNameNormalizer names, CallMapper mapper, Clock clock) {
        this.rooms=rooms; this.participants=participants; this.users=users; this.teams=teams;
        this.teamMembers=teamMembers; this.currentUser=currentUser; this.properties=properties;
        this.names=names; this.mapper=mapper; this.clock=clock;
    }

    @Transactional
    public LiveKitTokenResponse joinAndIssueToken(LiveKitTokenRequest request) {
        if (!properties.getLiveKit().hasCompleteConfiguration())
            throw new IllegalStateException("GROUP_CALLING_NOT_CONFIGURED");
        Long userId=currentUser.getCurrentUserId(); User user=activeUser(userId);
        String roomName=names.normalize(request == null ? null : request.room());
        List<CallRoom> found=rooms.findUsableRoomsByName(roomName, USABLE, PageRequest.of(0,1));
        CallRoom room;
        if (found.isEmpty()) {
            room=new CallRoom(); room.setRoomName(roomName); room.setRoomCreator(user);
            room.setStartedAt(clock.instant()); room.setStatus(CallRoomStatus.OPEN);
            if (request != null && request.teamId()!=null) {
                Team team=teams.findById(request.teamId()).filter(t -> Boolean.TRUE.equals(t.getActive()))
                        .orElseThrow(() -> new ResourceNotFoundException("Team was not found."));
                requireTeamAccess(team,userId); room.setTeam(team);
            }
            room=rooms.saveAndFlush(room);
        } else {
            room=rooms.findByRoomIdForUpdate(found.get(0).getRoomId()).orElseThrow();
            if (room.getTeam()!=null) requireTeamAccess(room.getTeam(),userId);
        }
        if (!participants.existsByRoomRoomIdAndUserUserIdAndLeftAtIsNull(room.getRoomId(), userId)) {
            if (participants.countByRoomRoomIdAndLeftAtIsNull(room.getRoomId()) >= properties.getGroup().getMaximumParticipants())
                throw new ConflictException("TEAM_ROOM_FULL", "The team room is full.");
            CallRoomParticipant attendance=new CallRoomParticipant(); attendance.setRoom(room); attendance.setUser(user);
            attendance.setParticipantRole(room.getRoomCreator().getUserId().equals(userId) ? CallRoomParticipantRole.HOST : CallRoomParticipantRole.MEMBER);
            attendance.setJoinedAt(clock.instant()); participants.save(attendance);
        }
        if (room.getStatus()==CallRoomStatus.OPEN) room.setStatus(CallRoomStatus.ACTIVE);
        Instant now=clock.instant(); String identity=String.valueOf(userId);
        String token=JWT.create().withIssuer(properties.getLiveKit().getApiKey()).withSubject(identity)
                .withClaim("name",user.getFullName())
                .withClaim("video",Map.of("room",roomName,"roomJoin",true,"canPublish",true,"canSubscribe",true,"canPublishData",true))
                .withIssuedAt(now).withExpiresAt(now.plus(properties.getLiveKit().getTokenLifetime()))
                .sign(Algorithm.HMAC256(properties.getLiveKit().getApiSecret()));
        return new LiveKitTokenResponse(token,properties.getLiveKit().getUrl(),roomName,identity,user.getFullName());
    }

    @Transactional
    public void leave(String roomName) {
        Long userId=currentUser.getCurrentUserId(); CallRoom room=latest(names.normalize(roomName));
        participants.markUserAsLeft(room.getRoomId(),userId,clock.instant());
    }

    @Transactional
    public void end(String roomId, boolean cancel) {
        CallRoom room=rooms.findByRoomIdForUpdate(roomId).orElseThrow(() -> new ResourceNotFoundException("Call room was not found."));
        if (!room.getRoomCreator().getUserId().equals(currentUser.getCurrentUserId()))
            throw new ForbiddenOperationException("Only the room creator can end this room.");
        if (room.getStatus()==CallRoomStatus.ENDED || room.getStatus()==CallRoomStatus.CANCELLED) return;
        Instant now=clock.instant(); room.setStatus(cancel ? CallRoomStatus.CANCELLED : CallRoomStatus.ENDED); room.setEndedAt(now);
        participants.closeAllActiveAttendance(roomId,now);
    }

    @Transactional(readOnly=true)
    public TeamRoomNotesResponse notes(String roomName) {
        CallRoom room=latest(names.normalize(roomName)); requireRoomAccess(room,currentUser.getCurrentUserId());
        User updater=room.getUpdatedBy()==null?null:users.findById(room.getUpdatedBy()).orElse(null);
        return mapper.toNotesResponse(room,updater);
    }

    @Transactional
    public TeamRoomNotesResponse updateNotes(String roomName, UpdateTeamRoomNotesRequest request) {
        Long userId=currentUser.getCurrentUserId(); CallRoom room=rooms.findByRoomIdForUpdate(latest(names.normalize(roomName)).getRoomId()).orElseThrow();
        requireRoomAccess(room,userId); room.setNotes(request.notes().trim()); room.setUpdatedBy(userId);
        return mapper.toNotesResponse(room,activeUser(userId));
    }

    @Transactional(readOnly=true)
    public List<CallRoomParticipantResponse> activeParticipants(String roomId) {
        CallRoom room=rooms.findAccessibleRoom(roomId,currentUser.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Call room was not found."));
        return participants.findActiveParticipants(room.getRoomId()).stream().map(mapper::toParticipantResponse).toList();
    }

    private CallRoom latest(String name) { return rooms.findUsableRoomsByName(name,USABLE,PageRequest.of(0,1)).stream().findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Active call room was not found.")); }
    private User activeUser(Long id) { return users.findById(id).filter(u->Boolean.TRUE.equals(u.getActive()))
            .orElseThrow(() -> new ResourceNotFoundException("User was not found.")); }
    private void requireTeamAccess(Team t,Long userId) { if (!t.getOwner().getUserId().equals(userId) && !teamMembers.existsByTeamTeamIdAndUserUserId(t.getTeamId(),userId))
        throw new ForbiddenOperationException("You are not a member of this team."); }
    private void requireRoomAccess(CallRoom room,Long userId) {
        if (room.getRoomCreator().getUserId().equals(userId)) return;
        if (room.getTeam()!=null) { requireTeamAccess(room.getTeam(),userId); return; }
        if (participants.findFirstByRoomRoomIdAndUserUserIdOrderByJoinedAtDesc(room.getRoomId(),userId).isEmpty())
            throw new ForbiddenOperationException("You cannot access this room.");
    }
}
