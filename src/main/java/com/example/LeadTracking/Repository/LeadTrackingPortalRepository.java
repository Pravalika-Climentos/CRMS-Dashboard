package com.example.LeadTracking.Repository;

import com.example.LeadTracking.Entity.LeadTrackingPortal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LeadTrackingPortalRepository
        extends JpaRepository<LeadTrackingPortal, Long> {

    Optional<LeadTrackingPortal> findByPortalKey(String portalKey);

    Optional<LeadTrackingPortal> findByTokenHashAndActiveTrue(String tokenHash);
}
