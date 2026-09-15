package com.example.Call.Entity;

import com.example.Common.Entity.BaseEntity;
import com.example.CRM.Entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "call_sessions",
    indexes = {
        @Index(
            name = "idx_calls_caller_started",
            columnList = "caller_id, started_at"
        ),
        @Index(
            name = "idx_calls_callee_started",
            columnList = "callee_id, started_at"
        ),
        @Index(
            name = "idx_calls_status_started",
            columnList = "status, started_at"
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
public class CallSession extends BaseEntity {

    @Id
    @Column(
        name = "call_id",
        nullable = false,
        length = 36,
        updatable = false
    )
    private String callId;

    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "caller_id",
        nullable = false
    )
    private User caller;

    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "callee_id",
        nullable = false
    )
    private User callee;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "call_type",
        nullable = false,
        length = 20
    )
    private CallType callType;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "status",
        nullable = false,
        length = 30
    )
    private CallStatus status = CallStatus.RINGING;

    @Column(
        name = "started_at",
        nullable = false,
        updatable = false
    )
    private Instant startedAt;

    @Column(name = "answered_at")
    private Instant answeredAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @Column(
        name = "end_reason",
        length = 100
    )
    private String endReason;

    @Column(
        name = "recording_path",
        length = 1024
    )
    private String recordingPath;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ended_by")
    private User endedBy;

    @Version
    @Column(
        name = "version",
        nullable = false
    )
    private Long version;

    @PrePersist
    private void prepareForInsert() {

        if (
            callId == null ||
            callId.isBlank()
        ) {
            callId =
                    UUID.randomUUID()
                        .toString();
        }

        if (startedAt == null) {
            startedAt = Instant.now();
        }

        if (status == null) {
            status = CallStatus.RINGING;
        }

        if (durationSeconds == null) {
            durationSeconds = 0L;
        }
    }
}