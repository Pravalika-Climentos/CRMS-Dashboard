package com.example.UserManagement.Controller;

import com.example.CRM.Entity.UserRole;
import com.example.Common.DTO.Response.PageResponse;
import com.example.UserManagement.DTO.AssignManagerRequest;
import com.example.UserManagement.DTO.ChangeUserActiveRequest;
import com.example.UserManagement.DTO.ChangeUserRoleRequest;
import com.example.UserManagement.DTO.UserManagementResponse;
import com.example.UserManagement.Service.UserManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserManagementController {

    private final UserManagementService userManagementService;

    @GetMapping("/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<UserManagementResponse> listUsers(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return userManagementService.listUsers(
                search,
                role,
                active,
                page,
                size
        );
    }

    @PatchMapping("/admin/users/{userId}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public UserManagementResponse changeRole(
            @PathVariable Long userId,
            @Valid @RequestBody ChangeUserRoleRequest request
    ) {
        return userManagementService.changeRole(
                userId,
                request.role()
        );
    }

    @PatchMapping("/admin/users/{userId}/manager")
    @PreAuthorize("hasRole('ADMIN')")
    public UserManagementResponse assignManager(
            @PathVariable Long userId,
            @RequestBody AssignManagerRequest request
    ) {
        return userManagementService.assignManager(
                userId,
                request.managerUserId()
        );
    }

    @PatchMapping("/admin/users/{userId}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public UserManagementResponse changeActive(
            @PathVariable Long userId,
            @Valid @RequestBody ChangeUserActiveRequest request
    ) {
        return userManagementService.changeActiveStatus(
                userId,
                request.active()
        );
    }

    @GetMapping("/users/my-team")
    @PreAuthorize("hasRole('MANAGER')")
    public List<UserManagementResponse> myTeam() {
        return userManagementService.getMyTeam();
    }

    @GetMapping("/users/sales-executives")
    @PreAuthorize("hasRole('MANAGER')")
    public List<UserManagementResponse> salesExecutives() {
        return userManagementService.getActiveSalesExecutives();
    }

    @GetMapping("/users/managers")
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserManagementResponse> managers() {
        return userManagementService.getActiveManagers();
    }
}