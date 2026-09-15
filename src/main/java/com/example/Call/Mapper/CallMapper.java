package com.example.Call.Mapper;

import com.example.Call.DTO.Response.CallResponse;
import com.example.Call.DTO.Response.CallRoomParticipantResponse;
import com.example.Call.DTO.Response.CallRoomResponse;
import com.example.Call.DTO.Response.TeamRoomNotesResponse;

import com.example.Call.Entity.CallRoom;
import com.example.Call.Entity.CallRoomParticipant;
import com.example.Call.Entity.CallSession;

import com.example.CRM.Entity.Team;
import com.example.CRM.Entity.User;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

@Component
public class CallMapper {

    public CallResponse toCallResponse(
            CallSession call
    ) {
        return new CallResponse(
                call.getCallId(),

                call.getCaller()
                        .getUserId(),

                call.getCallee()
                        .getUserId(),

                lowercase(
                        call.getCallType()
                ),

                lowercase(
                        call.getStatus()
                ),

                call.getStartedAt(),
                call.getAnsweredAt(),
                call.getEndedAt(),

                call.getDurationSeconds() == null
                        ? 0L
                        : call.getDurationSeconds(),

                call.getEndReason(),
                call.getRecordingPath()
        );
    }

    public CallRoomResponse toRoomResponse(
            CallRoom room,
            long activeParticipantCount
    ) {
        Team team = room.getTeam();
        User creator = room.getRoomCreator();

        return new CallRoomResponse(
                room.getRoomId(),
                room.getRoomName(),

                team == null
                        ? null
                        : team.getTeamId(),

                team == null
                        ? null
                        : team.getName(),

                creator.getUserId(),
                creator.getFullName(),

                lowercase(
                        room.getStatus()
                ),

                room.getStartedAt(),
                room.getEndedAt(),
                activeParticipantCount,
                room.getVersion()
        );
    }

    public CallRoomParticipantResponse
            toParticipantResponse(
                    CallRoomParticipant participant
            ) {

        User user = participant.getUser();

        return new CallRoomParticipantResponse(
                participant.getParticipantId(),
                user.getUserId(),
                user.getFullName(),
                user.getEmail(),
                user.getAvatar(),

                lowercase(
                        participant.getParticipantRole()
                ),

                participant.getJoinedAt(),
                participant.getLeftAt(),
                participant.getLeftAt() == null
        );
    }

    public TeamRoomNotesResponse toNotesResponse(
            CallRoom room,
            User lastUpdatedBy
    ) {
        return new TeamRoomNotesResponse(
                room.getRoomName(),

                room.getNotes() == null
                        ? ""
                        : room.getNotes(),

                room.getUpdatedBy(),

                lastUpdatedBy == null
                        ? null
                        : lastUpdatedBy.getFullName(),

                toInstant(
                        room.getUpdatedAt()
                )
        );
    }

    private String lowercase(
            Enum<?> value
    ) {
        if (value == null) {
            return null;
        }

        return value.name()
                .toLowerCase(
                        Locale.ROOT
                );
    }

    /*
     * BaseEntity currently uses LocalDateTime, while the Call
     * response contract uses Instant.
     *
     * The project stores Hibernate timestamps in UTC, so the
     * conversion uses UTC.
     */
    private Instant toInstant(
            LocalDateTime value
    ) {
        if (value == null) {
            return null;
        }

        return value.toInstant(
                ZoneOffset.UTC
        );
    }
}