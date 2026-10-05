package com.example.LeadTracking.Controller;

import com.example.LeadTracking.DTO.CreateLeadTrackingPortalRequest;
import com.example.LeadTracking.DTO.LeadTrackingPortalResponse;
import com.example.LeadTracking.Service.LeadTrackingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/lead-tracking-portal")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class LeadTrackingAdminController {

    private final LeadTrackingService tracking;

    @GetMapping
    public LeadTrackingPortalResponse get() {
        return tracking.getPortal();
    }

    @PostMapping
    public LeadTrackingPortalResponse create(
            @Valid @RequestBody(required = false) CreateLeadTrackingPortalRequest request
    ) {
        return tracking.createPortal(request);
    }

    @PostMapping("/regenerate")
    public LeadTrackingPortalResponse regenerate(
            @Valid @RequestBody(required = false) CreateLeadTrackingPortalRequest request
    ) {
        return tracking.regeneratePortal(request);
    }

    @DeleteMapping
    public ResponseEntity<Void> disable() {
        tracking.disablePortal();
        return ResponseEntity.noContent().build();
    }
}
