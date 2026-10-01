package com.example.LeadImport.DTO;

import jakarta.validation.constraints.NotEmpty;
import java.util.Map;

public record UpdateLeadImportRowRequest(
        @NotEmpty(message = "Lead values are required.")
        Map<String, String> values
) {}
