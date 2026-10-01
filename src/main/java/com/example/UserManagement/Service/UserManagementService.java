package com.example.UserManagement.Service;

import com.example.CRM.Entity.User;
import com.example.CRM.Entity.UserRole;
import com.example.CRM.Repository.UserRepository;
import com.example.Common.DTO.Response.PageResponse;
import com.example.Common.Exception.ConflictException;
import com.example.Common.Service.CurrentUserService;
import com.example.UserManagement.DTO.UserManagementResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class UserManagementService {

    private static final int MAXIMUM_PAGE_SIZE = 100;

    private final UserRepository users;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public PageResponse<UserManagementResponse> listUsers(
            String search,
            UserRole role,
            Boolean active,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAXIMUM_PAGE_SIZE);
        String safeSearch = search == null ? "" : search.trim();

        Page<User> result = users.searchForUserManagement(
                safeSearch,
                role,
                active,
                PageRequest.of(
                        safePage,
                        safeSize,
                        Sort.by(
                                Sort.Order.asc("fullName"),
                                Sort.Order.asc("userId")
                        )
                )
        );

        return new PageResponse<>(
                result.getContent()
                        .stream()
                        .map(this::toResponse)
                        .toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }

    @Transactional
    public UserManagementResponse changeRole(
            Long userId,
            UserRole requestedRole
    ) {
        User target = requiredUser(userId);
        UserRole currentRole = target.getRole();

        if (currentRole == requestedRole) {
            return toResponse(target);
        }

        if (currentRole == UserRole.ADMIN
                && requestedRole != UserRole.ADMIN
                && users.countByRoleAndActiveTrue(UserRole.ADMIN) <= 1) {
            throw new ConflictException(
                    "LAST_ACTIVE_ADMIN",
                    "The final active administrator cannot be demoted."
            );
        }

        if (currentRole == UserRole.MANAGER
                && requestedRole != UserRole.MANAGER
                && users.existsByManagerUserId(target.getUserId())) {
            throw new ConflictException(
                    "MANAGER_HAS_EXECUTIVES",
                    "Reassign this manager's Sales Executives before changing the manager's role."
            );
        }

        target.setRole(requestedRole);

        // Admin and Manager users do not report to a manager under
        // the current business structure.
        if (requestedRole != UserRole.SALES_EXECUTIVE) {
            target.setManager(null);
        }

        return toResponse(users.save(target));
    }

    @Transactional
    public UserManagementResponse assignManager(
            Long executiveUserId,
            Long managerUserId
    ) {
        User executive = requiredUser(executiveUserId);

        if (executive.getRole() != UserRole.SALES_EXECUTIVE) {
            throw new IllegalArgumentException(
                    "A manager can be assigned only to a Sales Executive."
            );
        }

        if (managerUserId == null) {
            executive.setManager(null);
            return toResponse(users.save(executive));
        }

        if (executive.getUserId().equals(managerUserId)) {
            throw new IllegalArgumentException(
                    "A user cannot be assigned as their own manager."
            );
        }

        User manager = requiredUser(managerUserId);

        if (manager.getRole() != UserRole.MANAGER) {
            throw new IllegalArgumentException(
                    "The selected user does not have the Manager role."
            );
        }

        if (!Boolean.TRUE.equals(manager.getActive())) {
            throw new IllegalArgumentException(
                    "An inactive Manager cannot receive Sales Executives."
            );
        }

        if (Boolean.TRUE.equals(manager.getAccountLocked())) {
            throw new IllegalArgumentException(
                    "A locked Manager cannot receive Sales Executives."
            );
        }

        executive.setManager(manager);
        return toResponse(users.save(executive));
    }

    @Transactional
    public UserManagementResponse changeActiveStatus(
            Long userId,
            boolean active
    ) {
        User target = requiredUser(userId);

        if (Boolean.TRUE.equals(target.getActive()) == active) {
            return toResponse(target);
        }

        if (!active
                && target.getRole() == UserRole.ADMIN
                && users.countByRoleAndActiveTrue(UserRole.ADMIN) <= 1) {
            throw new ConflictException(
                    "LAST_ACTIVE_ADMIN",
                    "The final active administrator cannot be deactivated."
            );
        }

        if (!active
                && target.getRole() == UserRole.MANAGER
                && users.existsByManagerUserId(target.getUserId())) {
            throw new ConflictException(
                    "MANAGER_HAS_EXECUTIVES",
                    "Reassign this manager's Sales Executives before deactivating the manager."
            );
        }

        // Prevent an administrator from accidentally disabling their
        // own current account.
        if (!active
                && target.getUserId().equals(
                        currentUserService.getCurrentUserId()
                )) {
            throw new ConflictException(
                    "CANNOT_DEACTIVATE_SELF",
                    "You cannot deactivate your own account."
            );
        }

        target.setActive(active);

        if (!active) {
            target.setManager(null);
        }

        return toResponse(users.save(target));
    }

    @Transactional(readOnly = true)
    public List<UserManagementResponse> getMyTeam() {
        Long managerId = currentUserService.getCurrentUserId();

        return users
                .findByManagerUserIdAndActiveTrueOrderByFullNameAsc(
                        managerId
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserManagementResponse> getActiveSalesExecutives() {
        return users
                .findByRoleAndActiveTrueOrderByFullNameAsc(
                        UserRole.SALES_EXECUTIVE
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserManagementResponse> getActiveManagers() {
        return users
                .findByRoleAndActiveTrueOrderByFullNameAsc(
                        UserRole.MANAGER
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private User requiredUser(Long userId) {
        return users.findById(userId)
                .orElseThrow(() -> new NoSuchElementException(
                        "User was not found."
                ));
    }

    private UserManagementResponse toResponse(User user) {
        User manager = user.getManager();

        return new UserManagementResponse(
                user.getUserId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getDesignation(),
                user.getRole(),
                manager == null ? null : manager.getUserId(),
                manager == null ? null : manager.getFullName(),
                Boolean.TRUE.equals(user.getActive()),
                Boolean.TRUE.equals(user.getAccountLocked())
        );
    }
}