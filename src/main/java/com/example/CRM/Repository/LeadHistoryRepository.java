package com.example.CRM.Repository;

import com.example.CRM.Entity.LeadHistory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

public interface LeadHistoryRepository
        extends JpaRepository<LeadHistory, Long> {

    @EntityGraph(attributePaths = {
            "oldAssignedUser",
            "newAssignedUser",
            "oldManagerOwner",
            "newManagerOwner",
            "changedByUser"
    })
    List<LeadHistory>
    findByLeadLeadIdOrderByCreatedAtDescHistoryIdDesc(
            Long leadId
    );

    @EntityGraph(attributePaths = {"lead", "lead.assignedUser", "lead.managerOwner", "changedByUser", "newManagerOwner", "newAssignedUser"})
    @Query("""
            SELECT history FROM LeadHistory history
            WHERE history.changeType = com.example.CRM.Entity.LeadHistoryType.CONVERTED
              AND history.createdAt > :since
            ORDER BY history.createdAt DESC, history.historyId DESC
            """)
    List<LeadHistory> findRecentAdminNotifications(
            @Param("since") LocalDateTime since,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"lead", "lead.assignedUser", "lead.managerOwner", "changedByUser", "newManagerOwner", "newAssignedUser"})
    @Query("""
            SELECT history FROM LeadHistory history
            WHERE history.createdAt > :since
              AND (
                history.newManagerOwner.userId = :userId
                OR (history.changeType = com.example.CRM.Entity.LeadHistoryType.CONVERTED
                    AND history.lead.managerOwner.userId = :userId)
              )
            ORDER BY history.createdAt DESC, history.historyId DESC
            """)
    List<LeadHistory> findRecentManagerNotifications(
            @Param("userId") Long userId,
            @Param("since") LocalDateTime since,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"lead", "lead.assignedUser", "lead.managerOwner", "changedByUser", "newManagerOwner", "newAssignedUser"})
    @Query("""
            SELECT history FROM LeadHistory history
            WHERE history.createdAt > :since
              AND (
                history.newAssignedUser.userId = :userId
                OR (history.changeType = com.example.CRM.Entity.LeadHistoryType.CONVERTED
                    AND history.lead.assignedUser.userId = :userId
                    AND (history.changedByUser IS NULL OR history.changedByUser.userId <> :userId))
              )
            ORDER BY history.createdAt DESC, history.historyId DESC
            """)
    List<LeadHistory> findRecentExecutiveNotifications(
            @Param("userId") Long userId,
            @Param("since") LocalDateTime since,
            Pageable pageable
    );
}
