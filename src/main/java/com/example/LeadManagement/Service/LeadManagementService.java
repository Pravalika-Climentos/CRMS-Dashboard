package com.example.LeadManagement.Service;

import com.example.CRM.Entity.*;
import com.example.CRM.Repository.LeadHistoryRepository;
import com.example.CRM.Repository.LeadRepository;
import com.example.CRM.Repository.UserRepository;
import com.example.Common.DTO.Response.PageResponse;
import com.example.Common.Exception.ConflictException;
import com.example.Common.Exception.ForbiddenOperationException;
import com.example.Common.Exception.ResourceNotFoundException;
import com.example.Common.Service.CurrentUserService;
import com.example.LeadManagement.DTO.LeadHistoryResponse;
import com.example.LeadManagement.DTO.LeadResponse;
import com.example.LeadManagement.DTO.LeadNotificationResponse;
import com.example.LeadManagement.DTO.LeadSummaryResponse;
import com.example.LeadManagement.Mapper.LeadManagementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.EnumMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LeadManagementService {

    private static final int MAXIMUM_PAGE_SIZE = 100;
    private static final int EXPORT_PAGE_SIZE = 500;
    private static final int MAX_NOTIFICATION_LIMIT = 20;
    private static final DateTimeFormatter EXPORT_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LeadRepository leads;
    private final LeadHistoryRepository history;
    private final UserRepository users;
    private final CurrentUserService currentUserService;
    private final LeadManagementMapper mapper;

    @Transactional(readOnly = true)
    public PageResponse<LeadResponse> listVisibleLeads(
            String search,
            LeadStatus status,
            int page,
            int size
    ) {
        User actor = currentUser();
        String safeSearch = search == null ? "" : search.trim();
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAXIMUM_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("leadId")
                )
        );

        Page<Lead> result = switch (actor.getRole()) {
            case ADMIN -> leads.searchAllVisible(safeSearch, status, pageable);
            case MANAGER -> leads.searchAssignedToManagerTeam(
                    actor.getUserId(), safeSearch, status, pageable
            );
            case SALES_EXECUTIVE -> leads.searchAssignedToUser(
                    actor.getUserId(), safeSearch, status, pageable
            );
        };

        return new PageResponse<>(
                result.getContent().stream().map(mapper::toResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public LeadSummaryResponse visibleSummary() {
        User actor = currentUser();
        Map<LeadStatus, Long> counts = new EnumMap<>(LeadStatus.class);
        for (LeadStatus status : LeadStatus.values()) counts.put(status, 0L);
        List<Object[]> groupedCounts = switch (actor.getRole()) {
            case ADMIN -> leads.countByStatusForPublicTracking();
            case MANAGER -> leads.countByStatusForManager(actor.getUserId());
            case SALES_EXECUTIVE -> leads.countByStatusForAssignedUser(
                    actor.getUserId());
        };
        for (Object[] row : groupedCounts) {
            counts.put((LeadStatus) row[0], (Long) row[1]);
        }
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        long newCount = counts.get(LeadStatus.NEW);
        long converted = counts.get(LeadStatus.CONVERTED);
        long lost = counts.get(LeadStatus.LOST);
        return new LeadSummaryResponse(
                total,
                newCount,
                Math.max(0, total - newCount - converted - lost),
                converted
        );
    }

    @Transactional(readOnly = true)
    public LeadResponse getVisibleLead(Long leadId) {
        User actor = currentUser();
        Lead lead = requiredLead(leadId);
        requireVisible(actor, lead);
        return mapper.toResponse(lead);
    }

    @Transactional(readOnly = true)
    public byte[] exportVisibleLeads(String search, LeadStatus status) {
        User actor = currentUser();
        if (actor.getRole() == UserRole.SALES_EXECUTIVE) {
            throw new ForbiddenOperationException(
                    "Only Administrators and Managers can export lead data."
            );
        }

        String safeSearch = search == null ? "" : search.trim();
        List<Lead> visibleLeads = new ArrayList<>();
        int pageNumber = 0;
        Page<Lead> result;
        do {
            PageRequest request = PageRequest.of(pageNumber++, EXPORT_PAGE_SIZE,
                    Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("leadId")));
            result = actor.getRole() == UserRole.ADMIN
                    ? leads.searchAllVisible(safeSearch, status, request)
                    : leads.searchAssignedToManagerTeam(
                            actor.getUserId(), safeSearch, status, request);
            visibleLeads.addAll(result.getContent());
        } while (result.hasNext());

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Leads");
            String[] columns = {
                    "Lead ID", "Lead Name", "Email", "Phone", "Status",
                    "Estimated Value", "Source", "Manager", "Sales Executive",
                    "Assigned At", "Created At", "Updated At"
            };

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.RED.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);

            Row header = sheet.createRow(0);
            for (int column = 0; column < columns.length; column++) {
                Cell cell = header.createCell(column);
                cell.setCellValue(columns[column]);
                cell.setCellStyle(headerStyle);
            }

            int rowNumber = 1;
            for (Lead lead : visibleLeads) {
                Row row = sheet.createRow(rowNumber++);
                setCell(row, 0, lead.getLeadId());
                setCell(row, 1, lead.getLeadName());
                setCell(row, 2, lead.getEmail());
                setCell(row, 3, lead.getPhone());
                setCell(row, 4, lead.getStatus() == null ? null : lead.getStatus().name());
                setCell(row, 5, lead.getEstimatedValue());
                setCell(row, 6, lead.getSource() == null ? null : lead.getSource().getSourceName());
                setCell(row, 7, lead.getManagerOwner() == null ? null : lead.getManagerOwner().getFullName());
                setCell(row, 8, lead.getAssignedUser() == null ? null : lead.getAssignedUser().getFullName());
                setCell(row, 9, formatDateTime(lead.getAssignedAt()));
                setCell(row, 10, formatDateTime(lead.getCreatedAt()));
                setCell(row, 11, formatDateTime(lead.getUpdatedAt()));
            }

            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                    0, Math.max(0, rowNumber - 1), 0, columns.length - 1));
            for (int column = 0; column < columns.length; column++) {
                sheet.autoSizeColumn(column);
                sheet.setColumnWidth(column,
                        Math.min(sheet.getColumnWidth(column) + 768, 12_000));
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Lead data could not be exported.", exception);
        }
    }

    @Transactional(readOnly = true)
    public List<LeadNotificationResponse> notifications(
            LocalDateTime since,
            int requestedLimit
    ) {
        User actor = currentUser();
        LocalDateTime safeSince = since == null
                ? LocalDateTime.now(ZoneOffset.UTC).minusDays(7)
                : since;
        int limit = Math.min(Math.max(requestedLimit, 1), MAX_NOTIFICATION_LIMIT);
        PageRequest pageable = PageRequest.of(0, limit);

        List<LeadHistory> entries = switch (actor.getRole()) {
            case ADMIN -> history.findRecentAdminNotifications(safeSince, pageable);
            case MANAGER -> history.findRecentManagerNotifications(
                    actor.getUserId(), safeSince, pageable);
            case SALES_EXECUTIVE -> history.findRecentExecutiveNotifications(
                    actor.getUserId(), safeSince, pageable);
        };
        return entries.stream().map(this::toNotification).toList();
    }

    @Transactional(readOnly = true)
    public List<LeadHistoryResponse> getVisibleHistory(Long leadId) {
        User actor = currentUser();
        Lead lead = requiredLead(leadId);
        requireVisible(actor, lead);

        return history.findByLeadLeadIdOrderByCreatedAtDescHistoryIdDesc(leadId)
                .stream()
                .map(mapper::toHistoryResponse)
                .toList();
    }

    @Transactional
    public LeadResponse updateStatus(Long leadId, LeadStatus requestedStatus, String note) {
        User actor = currentUser();
        Lead lead = requiredLead(leadId);
        requireVisible(actor, lead);

        LeadStatus oldStatus = lead.getStatus();
        if (oldStatus == requestedStatus) {
            return mapper.toResponse(lead);
        }

        lead.setStatus(requestedStatus);
        lead.setConverted(requestedStatus == LeadStatus.CONVERTED);
        Lead saved = leads.save(lead);

        LeadHistory entry = baseHistory(saved, actor, cleanNote(note));
        entry.setChangeType(requestedStatus == LeadStatus.CONVERTED
                ? LeadHistoryType.CONVERTED
                : LeadHistoryType.STATUS_CHANGED);
        entry.setOldStatus(oldStatus);
        entry.setNewStatus(requestedStatus);
        history.save(entry);

        return mapper.toResponse(saved);
    }

    @Transactional
    public LeadResponse assignLead(Long leadId, Long salesExecutiveId, String note) {
        User actor = currentUser();
        if (actor.getRole() == UserRole.SALES_EXECUTIVE) {
            throw new ForbiddenOperationException(
                    "Sales Executives cannot assign or reassign leads."
            );
        }

        Lead lead = requiredLead(leadId);
        if (actor.getRole() == UserRole.MANAGER) {
            requireVisible(actor, lead);
        }

        User executive = users.findById(salesExecutiveId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Sales Executive was not found."
                ));
        validateAssignmentTarget(actor, executive);

        User oldAssignee = lead.getAssignedUser();
        if (oldAssignee != null && oldAssignee.getUserId().equals(executive.getUserId())) {
            return mapper.toResponse(lead);
        }

        LeadStatus oldStatus = lead.getStatus();
        lead.setAssignedUser(executive);
        lead.setManagerOwner(executive.getManager());
        lead.setAssignedAt(LocalDateTime.now(ZoneOffset.UTC));
        if (lead.getStatus() == LeadStatus.NEW) {
            lead.setStatus(LeadStatus.ASSIGNED);
        }
        Lead saved = leads.save(lead);

        LeadHistory entry = baseHistory(saved, actor, cleanNote(note));
        entry.setChangeType(oldAssignee == null
                ? LeadHistoryType.ASSIGNED
                : LeadHistoryType.REASSIGNED);
        entry.setOldAssignedUser(oldAssignee);
        entry.setNewAssignedUser(executive);
        entry.setAssignmentStrategy(LeadAssignmentStrategy.MANUAL);
        if (oldStatus != saved.getStatus()) {
            entry.setOldStatus(oldStatus);
            entry.setNewStatus(saved.getStatus());
        }
        history.save(entry);

        return mapper.toResponse(saved);
    }

    @Transactional
    public LeadResponse assignManager(Long leadId, Long managerId, String note) {
        User actor = currentUser();
        if (actor.getRole() != UserRole.ADMIN) throw new ForbiddenOperationException("Only Administrators can assign leads to Managers.");
        Lead lead = requiredLead(leadId);
        User manager = users.findById(managerId).orElseThrow(() -> new ResourceNotFoundException("Manager was not found."));
        if (manager.getRole() != UserRole.MANAGER || !Boolean.TRUE.equals(manager.getActive()) || Boolean.TRUE.equals(manager.getAccountLocked())) throw new ConflictException("MANAGER_UNAVAILABLE", "Select an active, unlocked Manager.");
        User oldManager = lead.getManagerOwner();
        if (oldManager != null && oldManager.getUserId().equals(managerId)) return mapper.toResponse(lead);
        User oldAssignee = lead.getAssignedUser();
        boolean clearAssignee = oldAssignee != null && (oldAssignee.getManager() == null || !oldAssignee.getManager().getUserId().equals(managerId));
        lead.setManagerOwner(manager);
        if (clearAssignee) { lead.setAssignedUser(null); lead.setAssignedAt(null); lead.setStatus(LeadStatus.NEW); lead.setConverted(false); }
        Lead saved = leads.save(lead);
        LeadHistory entry = baseHistory(saved, actor, cleanNote(note));
        entry.setChangeType(oldManager == null ? LeadHistoryType.MANAGER_ASSIGNED : LeadHistoryType.MANAGER_REASSIGNED);
        entry.setOldManagerOwner(oldManager); entry.setNewManagerOwner(manager);
        entry.setOldAssignedUser(clearAssignee ? oldAssignee : null);
        entry.setAssignmentStrategy(LeadAssignmentStrategy.MANUAL);
        history.save(entry);
        return mapper.toResponse(saved);
    }

    private User currentUser() {
        Long userId = currentUserService.getCurrentUserId();
        return users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Authenticated user was not found."
                ));
    }

    private LeadNotificationResponse toNotification(LeadHistory entry) {
        Lead lead = entry.getLead();
        String leadName = lead == null ? "Lead" : lead.getLeadName();
        String actorName = entry.getChangedByUser() == null
                ? "A CRM user"
                : entry.getChangedByUser().getFullName();
        return switch (entry.getChangeType()) {
            case MANAGER_ASSIGNED, MANAGER_REASSIGNED -> new LeadNotificationResponse(
                    entry.getHistoryId(), lead.getLeadId(),
                    "Lead assigned to you",
                    actorName + " assigned “" + leadName + "” to you.",
                    entry.getCreatedAt());
            case ASSIGNED, REASSIGNED -> new LeadNotificationResponse(
                    entry.getHistoryId(), lead.getLeadId(),
                    "New lead assignment",
                    actorName + " assigned “" + leadName + "” to you.",
                    entry.getCreatedAt());
            case CONVERTED -> new LeadNotificationResponse(
                    entry.getHistoryId(), lead.getLeadId(),
                    "Lead converted",
                    actorName + " converted “" + leadName + "”.",
                    entry.getCreatedAt());
            default -> throw new IllegalStateException(
                    "Unsupported lead notification type: " + entry.getChangeType());
        };
    }

    private void setCell(Row row, int column, Object value) {
        Cell cell = row.createCell(column);
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else if (value != null) {
            cell.setCellValue(value.toString());
        }
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : EXPORT_DATE_TIME.format(value);
    }

    private Lead requiredLead(Long leadId) {
        return leads.findDetailedById(leadId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Lead was not found."
                ));
    }

    private void requireVisible(User actor, Lead lead) {
        if (actor.getRole() == UserRole.ADMIN) {
            return;
        }

        User assignee = lead.getAssignedUser();
        boolean visible = assignee != null && (
                actor.getRole() == UserRole.SALES_EXECUTIVE
                        && assignee.getUserId().equals(actor.getUserId())
                || actor.getRole() == UserRole.MANAGER
                        && assignee.getManager() != null
                        && assignee.getManager().getUserId().equals(actor.getUserId())
        ) || actor.getRole() == UserRole.MANAGER
                && lead.getManagerOwner() != null
                && lead.getManagerOwner().getUserId().equals(actor.getUserId());

        if (!visible) {
            // Return not-found semantics so callers cannot enumerate leads
            // owned by other teams.
            throw new ResourceNotFoundException("Lead was not found.");
        }
    }

    private void validateAssignmentTarget(User actor, User executive) {
        if (executive.getRole() != UserRole.SALES_EXECUTIVE) {
            throw new ConflictException(
                    "INVALID_LEAD_ASSIGNEE",
                    "Leads can be assigned only to a Sales Executive."
            );
        }
        if (!Boolean.TRUE.equals(executive.getActive())
                || Boolean.TRUE.equals(executive.getAccountLocked())) {
            throw new ConflictException(
                    "SALES_EXECUTIVE_UNAVAILABLE",
                    "The selected Sales Executive is inactive or locked."
            );
        }
        if (actor.getRole() == UserRole.MANAGER
                && (executive.getManager() == null
                || !executive.getManager().getUserId().equals(actor.getUserId()))) {
            throw new ForbiddenOperationException(
                    "Managers can assign leads only to Sales Executives in their own team."
            );
        }
    }

    private LeadHistory baseHistory(Lead lead, User actor, String note) {
        LeadHistory entry = new LeadHistory();
        entry.setLead(lead);
        entry.setChangedByUser(actor);
        entry.setNote(note);
        return entry;
    }

    private String cleanNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        return note.trim();
    }
}
