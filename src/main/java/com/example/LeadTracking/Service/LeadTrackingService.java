package com.example.LeadTracking.Service;

import com.example.CRM.Entity.Lead;
import com.example.CRM.Entity.LeadHistory;
import com.example.CRM.Entity.LeadStatus;
import com.example.CRM.Repository.LeadHistoryRepository;
import com.example.CRM.Repository.LeadRepository;
import com.example.Common.DTO.Response.PageResponse;
import com.example.Common.Exception.ResourceNotFoundException;
import com.example.LeadTracking.DTO.*;
import com.example.LeadTracking.Entity.LeadTrackingPortal;
import com.example.LeadTracking.Repository.LeadTrackingPortalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LeadTrackingService {

    private static final String PORTAL_KEY = "ORGANIZATION_LEADS";
    private static final String DEFAULT_ORGANIZATION_NAME = "CRMS Lead Tracking";
    private static final int MAXIMUM_PAGE_SIZE = 100;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final LeadTrackingPortalRepository portals;
    private final LeadRepository leads;
    private final LeadHistoryRepository history;

    @Value("${lead.tracking.base-url:http://localhost:3000}")
    private String trackingBaseUrl;

    @Transactional(readOnly = true)
    public LeadTrackingPortalResponse getPortal() {
        return portals.findByPortalKey(PORTAL_KEY)
                .map(portal -> toPortalResponse(portal, null, false))
                .orElse(new LeadTrackingPortalResponse(
                        null, DEFAULT_ORGANIZATION_NAME, false,
                        null, null, null, null, false));
    }

    @Transactional
    public LeadTrackingPortalResponse createPortal(
            CreateLeadTrackingPortalRequest request
    ) {
        LeadTrackingPortal portal = portals.findByPortalKey(PORTAL_KEY)
                .orElseGet(LeadTrackingPortal::new);
        if (portal.getTrackingPortalId() != null
                && Boolean.TRUE.equals(portal.getActive())
                && !isExpired(portal)) {
            return toPortalResponse(portal, null, false);
        }
        return activateWithNewToken(portal, request);
    }

    @Transactional
    public LeadTrackingPortalResponse regeneratePortal(
            CreateLeadTrackingPortalRequest request
    ) {
        LeadTrackingPortal portal = portals.findByPortalKey(PORTAL_KEY)
                .orElseGet(LeadTrackingPortal::new);
        return activateWithNewToken(portal, request);
    }

    @Transactional
    public void disablePortal() {
        LeadTrackingPortal portal = portals.findByPortalKey(PORTAL_KEY)
                .orElseThrow(this::unavailable);
        portal.setActive(false);
        portal.setRevokedAt(nowUtc());
        portals.save(portal);
    }

    @Transactional
    public PageResponse<PublicLeadResponse> listPublicLeads(
            String rawToken,
            String search,
            LeadStatus status,
            String assignee,
            int page,
            int size
    ) {
        requireValidPortal(rawToken);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAXIMUM_PAGE_SIZE);
        String safeSearch = search == null ? "" : search.trim();
        String safeAssignee = assignee == null ? "" : assignee.trim();
        Page<Lead> result = leads.searchForPublicTracking(
                safeSearch,
                status,
                safeAssignee,
                PageRequest.of(safePage, safeSize,
                        Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("leadId")))
        );
        return new PageResponse<>(
                result.getContent().stream().map(this::toPublicLead).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }

    @Transactional
    public List<String> publicAssignees(String rawToken) {
        requireValidPortal(rawToken);
        return List.copyOf(leads.findPublicAssigneeNames());
    }

    @Transactional
    public PublicLeadSummaryResponse publicSummary(String rawToken) {
        LeadTrackingPortal portal = requireValidPortal(rawToken);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (LeadStatus status : LeadStatus.values()) counts.put(status.name(), 0L);
        for (Object[] row : leads.countByStatusForPublicTracking()) {
            counts.put(((LeadStatus) row[0]).name(), (Long) row[1]);
        }
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return new PublicLeadSummaryResponse(
                portal.getOrganizationName(), total,
                Collections.unmodifiableMap(counts));
    }

    @Transactional
    public PublicLeadDetailResponse publicLead(
            String rawToken,
            String reference
    ) {
        requireValidPortal(rawToken);
        Lead lead = leads.findByPublicReference(normalizeReference(reference))
                .orElseThrow(this::unavailable);
        List<LeadHistory> entries = history
                .findByLeadLeadIdOrderByCreatedAtDescHistoryIdDesc(lead.getLeadId());
        List<PublicLeadTimelineItem> timeline = publicTimeline(lead, entries);
        return new PublicLeadDetailResponse(
                lead.getPublicReference(),
                lead.getLeadName(),
                lead.getStatus(),
                lead.getSource() == null ? null : lead.getSource().getSourceName(),
                lead.getInterestedService(),
                lead.getAssignedUser() == null ? null : lead.getAssignedUser().getFullName(),
                lead.getCreatedAt(),
                lead.getUpdatedAt(),
                timeline
        );
    }

    private LeadTrackingPortalResponse activateWithNewToken(
            LeadTrackingPortal portal,
            CreateLeadTrackingPortalRequest request
    ) {
        String token = generateToken();
        portal.setPortalKey(PORTAL_KEY);
        portal.setOrganizationName(cleanOrganizationName(request));
        portal.setTokenHash(hashToken(token));
        portal.setActive(true);
        portal.setExpiresAt(request == null ? null : request.expiresAt());
        portal.setRevokedAt(null);
        LeadTrackingPortal saved = portals.save(portal);
        return toPortalResponse(saved, token, true);
    }

    private LeadTrackingPortal requireValidPortal(String rawToken) {
        if (rawToken == null || rawToken.length() < 40 || rawToken.length() > 256) {
            throw unavailable();
        }
        LeadTrackingPortal portal = portals
                .findByTokenHashAndActiveTrue(hashToken(rawToken))
                .orElseThrow(this::unavailable);
        if (isExpired(portal)) throw unavailable();
        portal.setLastAccessedAt(nowUtc());
        return portals.save(portal);
    }

    private List<PublicLeadTimelineItem> publicTimeline(
            Lead lead,
            List<LeadHistory> entries
    ) {
        List<PublicLeadTimelineItem> result = new ArrayList<>();
        List<LeadHistory> chronological = new ArrayList<>(entries);
        Collections.reverse(chronological);
        LeadStatus previous = null;
        for (LeadHistory entry : chronological) {
            LeadStatus status = entry.getNewStatus();
            if (status == null || status == previous) continue;
            result.add(new PublicLeadTimelineItem(status, entry.getCreatedAt()));
            previous = status;
        }
        if (result.isEmpty() || result.get(result.size() - 1).status() != lead.getStatus()) {
            result.add(new PublicLeadTimelineItem(
                    lead.getStatus(),
                    lead.getUpdatedAt() == null ? lead.getCreatedAt() : lead.getUpdatedAt()));
        }
        return List.copyOf(result);
    }

    private PublicLeadResponse toPublicLead(Lead lead) {
        return new PublicLeadResponse(
                lead.getPublicReference(),
                lead.getLeadName(),
                lead.getStatus(),
                lead.getSource() == null ? null : lead.getSource().getSourceName(),
                lead.getInterestedService(),
                lead.getAssignedUser() == null ? null : lead.getAssignedUser().getFullName(),
                lead.getCreatedAt(),
                lead.getUpdatedAt()
        );
    }

    private LeadTrackingPortalResponse toPortalResponse(
            LeadTrackingPortal portal,
            String token,
            boolean tokenShownOnce
    ) {
        String url = token == null ? null
                : normalizedBaseUrl() + "/#/organization/" + token;
        return new LeadTrackingPortalResponse(
                portal.getTrackingPortalId(),
                portal.getOrganizationName(),
                Boolean.TRUE.equals(portal.getActive()) && !isExpired(portal),
                portal.getExpiresAt(),
                portal.getCreatedAt(),
                portal.getLastAccessedAt(),
                url,
                tokenShownOnce
        );
    }

    private String cleanOrganizationName(CreateLeadTrackingPortalRequest request) {
        if (request == null || request.organizationName() == null
                || request.organizationName().isBlank()) {
            return DEFAULT_ORGANIZATION_NAME;
        }
        return request.organizationName().trim();
    }

    private String normalizeReference(String reference) {
        if (reference == null || !reference.matches("(?i)^LD-[A-Z0-9]{12}$")) {
            throw unavailable();
        }
        return reference.toUpperCase(Locale.ROOT);
    }

    private boolean isExpired(LeadTrackingPortal portal) {
        return portal.getExpiresAt() != null
                && !portal.getExpiresAt().isAfter(nowUtc());
    }

    private String normalizedBaseUrl() {
        return trackingBaseUrl.replaceAll("/+$", "");
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private ResourceNotFoundException unavailable() {
        return new ResourceNotFoundException("Tracking link is unavailable.");
    }
}
