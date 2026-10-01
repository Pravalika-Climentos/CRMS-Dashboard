package com.example.LeadImport.DTO;
import java.util.List;
import java.util.Map;
public record LeadImportRowResponse(Long rowId,int rowNumber,Map<String,String> rawData,Map<String,String> normalizedData,List<String> errors,Map<String,String> fieldErrors,boolean valid,boolean duplicate,Long importedLeadId){}
