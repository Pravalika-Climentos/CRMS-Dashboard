package com.example.LeadImport.Repository;
import com.example.LeadImport.Entity.LeadImport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface LeadImportRepository extends JpaRepository<LeadImport,Long>{
 @EntityGraph(attributePaths="uploadedBy") Page<LeadImport> findAllByOrderByCreatedAtDesc(Pageable pageable);
 @EntityGraph(attributePaths="uploadedBy") Page<LeadImport> findByUploadedByUserIdOrderByCreatedAtDesc(Long userId,Pageable pageable);
 @EntityGraph(attributePaths="uploadedBy")
 @Query(value="select distinct i from LeadImport i left join LeadImportRow r on r.leadImport=i left join r.importedLead l left join l.assignedUser a left join l.managerOwner m where i.uploadedBy.userId=:managerId or a.manager.userId=:managerId or m.userId=:managerId",
        countQuery="select count(distinct i.importId) from LeadImport i left join LeadImportRow r on r.leadImport=i left join r.importedLead l left join l.assignedUser a left join l.managerOwner m where i.uploadedBy.userId=:managerId or a.manager.userId=:managerId or m.userId=:managerId")
 Page<LeadImport> findVisibleToManager(@Param("managerId") Long managerId,Pageable pageable);
 @EntityGraph(attributePaths="uploadedBy") Optional<LeadImport> findDetailedByImportId(Long importId);
}
