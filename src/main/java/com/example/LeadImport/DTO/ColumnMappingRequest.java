package com.example.LeadImport.DTO;
import jakarta.validation.constraints.NotEmpty;
import java.util.Map;
public record ColumnMappingRequest(@NotEmpty(message="Map at least one CRM field.") Map<String,String> fieldMappings){}
