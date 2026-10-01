package com.example.LeadImport.DTO;

import com.example.CRM.Entity.LeadStatus;
import java.time.LocalDateTime;

public record LeadImportProgressLeadResponse(
        Long leadId,
        String leadName,
        String email,
        LeadStatus status,
        Long salesExecutiveId,
        String salesExecutiveName,
        LocalDateTime updatedAt
) {}
