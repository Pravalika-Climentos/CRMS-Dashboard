package com.example.CRM.Entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "lead_history",
        indexes = {
                @Index(
                        name = "idx_lead_history_lead_created",
                        columnList = "lead_id,created_at"
                ),
                @Index(
                        name = "idx_lead_history_changed_by",
                        columnList = "changed_by_user_id,created_at"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class LeadHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long historyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "lead_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_lead_history_lead"
            )
    )
    private Lead lead;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 30)
    private LeadHistoryType changeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status", length = 40)
    private LeadStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", length = 40)
    private LeadStatus newStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "old_assigned_user_id",
            foreignKey = @ForeignKey(
                    name = "fk_lead_history_old_assignee"
            )
    )
    private User oldAssignedUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "new_assigned_user_id",
            foreignKey = @ForeignKey(
                    name = "fk_lead_history_new_assignee"
            )
    )
    private User newAssignedUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "old_manager_owner_id", foreignKey = @ForeignKey(name = "fk_lead_history_old_manager"))
    private User oldManagerOwner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "new_manager_owner_id", foreignKey = @ForeignKey(name = "fk_lead_history_new_manager"))
    private User newManagerOwner;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_strategy", length = 30)
    private LeadAssignmentStrategy assignmentStrategy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "changed_by_user_id",
            foreignKey = @ForeignKey(
                    name = "fk_lead_history_changed_by"
            )
    )
    private User changedByUser;

    @Column(length = 1000)
    private String note;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    @PrePersist
    void beforeInsert() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
