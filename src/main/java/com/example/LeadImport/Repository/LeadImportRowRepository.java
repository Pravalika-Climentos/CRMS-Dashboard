package com.example.LeadImport.Repository;
import com.example.LeadImport.Entity.LeadImportRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.List;
import java.util.Optional;
public interface LeadImportRowRepository extends JpaRepository<LeadImportRow,Long>{
 Page<LeadImportRow> findByLeadImportImportIdOrderByRowNumber(Long importId,Pageable pageable);
 List<LeadImportRow> findByLeadImportImportIdOrderByRowNumber(Long importId);
 Optional<LeadImportRow> findByRowIdAndLeadImportImportId(Long rowId,Long importId);
 @EntityGraph(attributePaths={"importedLead","importedLead.assignedUser"})
 List<LeadImportRow> findByLeadImportImportIdAndImportedLeadIsNotNullOrderByRowNumber(Long importId);
 @EntityGraph(attributePaths={"importedLead","importedLead.assignedUser"})
 List<LeadImportRow> findByLeadImportImportIdAndImportedLeadAssignedUserManagerUserIdOrderByRowNumber(Long importId,Long managerId);
 boolean existsByLeadImportImportIdAndImportedLeadAssignedUserManagerUserId(Long importId,Long managerId);
 @EntityGraph(attributePaths={"importedLead","importedLead.assignedUser","importedLead.managerOwner"})
 @org.springframework.data.jpa.repository.Query("select r from LeadImportRow r left join r.importedLead l left join l.assignedUser a left join l.managerOwner m where r.leadImport.importId=:importId and (a.manager.userId=:managerId or m.userId=:managerId) order by r.rowNumber")
 List<LeadImportRow> findVisibleToManager(Long importId,Long managerId);
 @org.springframework.data.jpa.repository.Query("select (count(r)>0) from LeadImportRow r left join r.importedLead l left join l.assignedUser a left join l.managerOwner m where r.leadImport.importId=:importId and (a.manager.userId=:managerId or m.userId=:managerId)")
 boolean existsVisibleToManager(Long importId,Long managerId);
 long deleteByLeadImportImportId(Long importId);
}
