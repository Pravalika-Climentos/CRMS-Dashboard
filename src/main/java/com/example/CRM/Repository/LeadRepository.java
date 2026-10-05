package com.example.CRM.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.example.CRM.Entity.Lead;
import com.example.CRM.Entity.LeadStatus;

import java.util.List;
import java.util.Optional;

public interface LeadRepository extends JpaRepository<Lead, Long> {

    @EntityGraph(attributePaths = {"company", "contact", "source", "assignedUser", "managerOwner"})
    @Query("""
        SELECT lead FROM Lead lead
        WHERE (:status IS NULL OR lead.status = :status)
          AND (:search = ''
            OR LOWER(lead.leadName) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(COALESCE(lead.email, '')) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(COALESCE(lead.phone, '')) LIKE LOWER(CONCAT('%', :search, '%')))
        """)
    Page<Lead> searchAllVisible(
            @Param("search") String search,
            @Param("status") LeadStatus status,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"source", "assignedUser"})
    @Query("""
        SELECT lead FROM Lead lead
        LEFT JOIN lead.assignedUser publicAssignee
        WHERE (:status IS NULL OR lead.status = :status)
          AND (:search = ''
            OR LOWER(lead.leadName) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(lead.publicReference) LIKE LOWER(CONCAT('%', :search, '%')))
          AND (:assignee = '' OR LOWER(publicAssignee.fullName) = LOWER(:assignee))
        """)
    Page<Lead> searchForPublicTracking(
            @Param("search") String search,
            @Param("status") LeadStatus status,
            @Param("assignee") String assignee,
            Pageable pageable
    );

    @Query("""
        SELECT DISTINCT user.fullName
        FROM Lead lead JOIN lead.assignedUser user
        WHERE user.fullName IS NOT NULL AND user.fullName <> ''
        ORDER BY user.fullName
        """)
    List<String> findPublicAssigneeNames();

    @EntityGraph(attributePaths = {"company", "contact", "source", "assignedUser"})
    @Query("""
        SELECT lead FROM Lead lead
        WHERE lead.assignedUser.userId = :userId
          AND (:status IS NULL OR lead.status = :status)
          AND (:search = ''
            OR LOWER(lead.leadName) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(COALESCE(lead.email, '')) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(COALESCE(lead.phone, '')) LIKE LOWER(CONCAT('%', :search, '%')))
        """)
    Page<Lead> searchAssignedToUser(
            @Param("userId") Long userId,
            @Param("search") String search,
            @Param("status") LeadStatus status,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"company", "contact", "source", "assignedUser"})
    @Query("""
        SELECT lead FROM Lead lead
        WHERE (lead.managerOwner.userId = :managerId OR lead.assignedUser.manager.userId = :managerId)
          AND (:status IS NULL OR lead.status = :status)
          AND (:search = ''
            OR LOWER(lead.leadName) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(COALESCE(lead.email, '')) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(COALESCE(lead.phone, '')) LIKE LOWER(CONCAT('%', :search, '%')))
        """)
    Page<Lead> searchAssignedToManagerTeam(
            @Param("managerId") Long managerId,
            @Param("search") String search,
            @Param("status") LeadStatus status,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"company", "contact", "source", "assignedUser", "assignedUser.manager", "managerOwner"})
    @Query("SELECT lead FROM Lead lead WHERE lead.leadId = :leadId")
    Optional<Lead> findDetailedById(@Param("leadId") Long leadId);

    List<Lead> findByAssignedUser_UserId(Long userId);

    List<Lead> findBySource_SourceId(Long sourceId);

    List<Lead> findByStatus(LeadStatus status);

    List<Lead> findByConverted(boolean converted);
    Optional<Lead> findFirstByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByPhone(String phone);
    boolean existsByDuplicateHash(String duplicateHash);

    @EntityGraph(attributePaths = {"source"})
    Optional<Lead> findByPublicReference(String publicReference);

    @Query("""
        SELECT lead.status, COUNT(lead)
        FROM Lead lead
        GROUP BY lead.status
        """)
    List<Object[]> countByStatusForPublicTracking();

    @Query("""
        SELECT lead.status, COUNT(lead)
        FROM Lead lead
        WHERE lead.managerOwner.userId = :managerId
           OR lead.assignedUser.manager.userId = :managerId
        GROUP BY lead.status
        """)
    List<Object[]> countByStatusForManager(@Param("managerId") Long managerId);

    @Query("""
        SELECT lead.status, COUNT(lead)
        FROM Lead lead
        WHERE lead.assignedUser.userId = :userId
        GROUP BY lead.status
        """)
    List<Object[]> countByStatusForAssignedUser(@Param("userId") Long userId);
}
