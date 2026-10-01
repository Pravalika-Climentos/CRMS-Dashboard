package com.example.LeadManagement.Mapper;

import com.example.CRM.Entity.*;
import com.example.LeadManagement.DTO.LeadHistoryResponse;
import com.example.LeadManagement.DTO.LeadResponse;
import org.springframework.stereotype.Component;

@Component
public class LeadManagementMapper {

    public LeadResponse toResponse(Lead lead) {
        Company company = lead.getCompany();
        Contact contact = lead.getContact();
        LeadSource source = lead.getSource();
        User assignedUser = lead.getAssignedUser();
        User managerOwner = lead.getManagerOwner();

        return new LeadResponse(
                lead.getLeadId(),
                lead.getLeadName(),
                lead.getEmail(),
                lead.getPhone(),
                lead.getStatus(),
                lead.getEstimatedValue(),
                Boolean.TRUE.equals(lead.getConverted()),
                company == null ? null : company.getCompanyId(),
                company == null ? null : company.getCompanyName(),
                contact == null ? null : contact.getContactId(),
                contact == null ? null : contactName(contact),
                source == null ? null : source.getSourceId(),
                source == null ? null : source.getSourceName(),
                assignedUser == null ? null : assignedUser.getUserId(),
                assignedUser == null ? null : assignedUser.getFullName(),
                managerOwner == null ? null : managerOwner.getUserId(),
                managerOwner == null ? null : managerOwner.getFullName(),
                lead.getAssignedAt(),
                lead.getVersion(),
                lead.getCreatedAt(),
                lead.getUpdatedAt()
        );
    }

    public LeadHistoryResponse toHistoryResponse(LeadHistory history) {
        User oldAssignee = history.getOldAssignedUser();
        User newAssignee = history.getNewAssignedUser();
        User changedBy = history.getChangedByUser();
        User oldManager = history.getOldManagerOwner();
        User newManager = history.getNewManagerOwner();

        return new LeadHistoryResponse(
                history.getHistoryId(),
                history.getChangeType(),
                history.getOldStatus(),
                history.getNewStatus(),
                oldAssignee == null ? null : oldAssignee.getUserId(),
                oldAssignee == null ? null : oldAssignee.getFullName(),
                newAssignee == null ? null : newAssignee.getUserId(),
                newAssignee == null ? null : newAssignee.getFullName(),
                oldManager == null ? null : oldManager.getUserId(),
                oldManager == null ? null : oldManager.getFullName(),
                newManager == null ? null : newManager.getUserId(),
                newManager == null ? null : newManager.getFullName(),
                history.getAssignmentStrategy(),
                changedBy == null ? null : changedBy.getUserId(),
                changedBy == null ? null : changedBy.getFullName(),
                history.getNote(),
                history.getCreatedAt()
        );
    }

    private String contactName(Contact contact) {
        return (safe(contact.getFirstName()) + " " + safe(contact.getLastName())).trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
