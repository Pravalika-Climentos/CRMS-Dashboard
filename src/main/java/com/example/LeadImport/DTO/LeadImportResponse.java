package com.example.LeadImport.DTO;
import com.example.LeadImport.Entity.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
public record LeadImportResponse(Long importId,String fileName,LeadImportFileType fileType,LeadImportStatus status,Long uploadedByUserId,String uploadedByName,List<String> detectedColumns,Map<String,String> columnMapping,int totalRecords,int validRecords,int invalidRecords,int duplicateRecords,int importedRecords,String failureMessage,LocalDateTime createdAt,LocalDateTime completedAt,Map<String,String> suggestedMapping){}
