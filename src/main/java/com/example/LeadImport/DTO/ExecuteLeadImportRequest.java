package com.example.LeadImport.DTO;
import java.util.List;
public record ExecuteLeadImportRequest(List<Long> salesExecutiveIds,List<Long> managerIds){}
