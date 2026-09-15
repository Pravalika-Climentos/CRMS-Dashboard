package com.example.Call.DTO.Response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record CallResponse(

        /*
         * Original frontend expects the database-style name id.
         */
        @JsonProperty("id")
        String callId,

        @JsonProperty("caller_id")
        Long callerId,

        @JsonProperty("callee_id")
        Long calleeId,

        @JsonProperty("call_type")
        String callType,

        @JsonProperty("status")
        String status,

        @JsonProperty("started_at")
        Instant startedAt,

        @JsonProperty("answered_at")
        Instant answeredAt,

        @JsonProperty("ended_at")
        Instant endedAt,

        @JsonProperty("duration_seconds")
        Long durationSeconds,

        @JsonProperty("end_reason")
        String endReason,

        @JsonProperty("recording_path")
        String recordingPath
) {
}