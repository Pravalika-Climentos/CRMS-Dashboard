package com.example.LeadImport.DTO;

import com.example.CRM.Entity.LeadStatus;
import java.util.List;
import java.util.Map;

public record LeadImportProgressResponse(
        Long importId,
        String fileName,
        long importedLeads,
        Map<LeadStatus, Long> statusCounts,
        List<LeadImportAssigneeProgressResponse> assignees,
        List<LeadImportProgressLeadResponse> leads
) {}
