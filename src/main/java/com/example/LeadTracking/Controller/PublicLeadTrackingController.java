package com.example.LeadTracking.Controller;

import com.example.CRM.Entity.LeadStatus;
import com.example.Common.DTO.Response.PageResponse;
import com.example.LeadTracking.DTO.PublicLeadDetailResponse;
import com.example.LeadTracking.DTO.PublicLeadResponse;
import com.example.LeadTracking.DTO.PublicLeadSummaryResponse;
import com.example.LeadTracking.Service.LeadTrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public/lead-tracking")
@RequiredArgsConstructor
public class PublicLeadTrackingController {

    private static final String TOKEN_HEADER = "X-Lead-Tracking-Token";
    private final LeadTrackingService tracking;

    @GetMapping("/leads")
    public ResponseEntity<PageResponse<PublicLeadResponse>> list(
            @RequestHeader(TOKEN_HEADER) String token,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) LeadStatus status,
            @RequestParam(defaultValue = "") String assignee,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return publicResponse(tracking.listPublicLeads(
                token, search, status, assignee, page, size));
    }

    @GetMapping("/assignees")
    public ResponseEntity<java.util.List<String>> assignees(
            @RequestHeader(TOKEN_HEADER) String token
    ) {
        return publicResponse(tracking.publicAssignees(token));
    }

    @GetMapping("/summary")
    public ResponseEntity<PublicLeadSummaryResponse> summary(
            @RequestHeader(TOKEN_HEADER) String token
    ) {
        return publicResponse(tracking.publicSummary(token));
    }

    @GetMapping("/leads/{reference}")
    public ResponseEntity<PublicLeadDetailResponse> detail(
            @RequestHeader(TOKEN_HEADER) String token,
            @PathVariable String reference
    ) {
        return publicResponse(tracking.publicLead(token, reference));
    }

    private <T> ResponseEntity<T> publicResponse(T body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Robots-Tag", "noindex, nofollow")
                .header("Referrer-Policy", "no-referrer")
                .body(body);
    }
}
