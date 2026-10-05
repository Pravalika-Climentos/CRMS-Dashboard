package com.example.LeadManagement.Controller;

import com.example.CRM.Entity.LeadStatus;
import com.example.Common.DTO.Response.PageResponse;
import com.example.LeadManagement.DTO.AssignLeadRequest;
import com.example.LeadManagement.DTO.AssignLeadManagerRequest;
import com.example.LeadManagement.DTO.LeadHistoryResponse;
import com.example.LeadManagement.DTO.LeadResponse;
import com.example.LeadManagement.DTO.LeadNotificationResponse;
import com.example.LeadManagement.DTO.LeadSummaryResponse;
import com.example.LeadManagement.DTO.UpdateLeadStatusRequest;
import com.example.LeadManagement.Service.LeadManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/leads")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SALES_EXECUTIVE')")
public class LeadManagementController {

    private final LeadManagementService leadManagementService;

    @GetMapping
    public PageResponse<LeadResponse> list(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) LeadStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return leadManagementService.listVisibleLeads(search, status, page, size);
    }

    @GetMapping("/summary")
    public LeadSummaryResponse summary() {
        return leadManagementService.visibleSummary();
    }

    @GetMapping("/export")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<byte[]> export(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) LeadStatus status
    ) {
        byte[] workbook = leadManagementService.exportVisibleLeads(search, status);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=lead-data-" + java.time.LocalDate.now() + ".xlsx")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(workbook.length)
                .body(workbook);
    }

    @GetMapping("/notifications")
    public List<LeadNotificationResponse> notifications(
            @RequestParam(required = false) LocalDateTime since,
            @RequestParam(defaultValue = "5") int limit
    ) {
        return leadManagementService.notifications(since, limit);
    }

    @GetMapping("/{leadId}")
    public LeadResponse get(@PathVariable Long leadId) {
        return leadManagementService.getVisibleLead(leadId);
    }

    @GetMapping("/{leadId}/history")
    public List<LeadHistoryResponse> history(@PathVariable Long leadId) {
        return leadManagementService.getVisibleHistory(leadId);
    }

    @PatchMapping("/{leadId}/status")
    public LeadResponse updateStatus(
            @PathVariable Long leadId,
            @Valid @RequestBody UpdateLeadStatusRequest request
    ) {
        return leadManagementService.updateStatus(
                leadId,
                request.status(),
                request.note()
        );
    }

    @PatchMapping("/{leadId}/assignment")
    @PreAuthorize("hasRole('MANAGER')")
    public LeadResponse assign(
            @PathVariable Long leadId,
            @Valid @RequestBody AssignLeadRequest request
    ) {
        return leadManagementService.assignLead(
                leadId,
                request.salesExecutiveId(),
                request.note()
        );
    }

    @PatchMapping("/{leadId}/manager")
    @PreAuthorize("hasRole('ADMIN')")
    public LeadResponse assignManager(@PathVariable Long leadId,@Valid @RequestBody AssignLeadManagerRequest request) {
        return leadManagementService.assignManager(leadId,request.managerId(),request.note());
    }
}
