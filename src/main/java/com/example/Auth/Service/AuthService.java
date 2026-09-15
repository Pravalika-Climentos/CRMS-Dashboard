package com.example.Auth.Service;

import com.example.Auth.DTO.ChangePasswordRequest;
import com.example.Auth.DTO.LoginRequest;
import com.example.Auth.DTO.LoginResponse;
import com.example.Auth.DTO.RegisterRequest;
import com.example.Auth.DTO.UserSessionResponse;
import com.example.Auth.Security.JwtService;
import com.example.CRM.Entity.User;
import com.example.CRM.Repository.UserRepository;
import com.example.Common.Service.CurrentUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtService jwtService;
    private final CurrentUserService currentUserService;
    private final int maximumAttempts;

    public AuthService(UserRepository users, PasswordEncoder passwords, JwtService jwtService,
                       CurrentUserService currentUserService,
                       @Value("${security.login.maximum-failed-attempts:5}") int maximumAttempts) {
        this.users = users;
        this.passwords = passwords;
        this.jwtService = jwtService;
        this.currentUserService = currentUserService;
        this.maximumAttempts = maximumAttempts;
    }

    @Transactional(noRollbackFor = {InvalidCredentialsException.class, AccountLockedException.class})
    public LoginResponse login(LoginRequest request) {
        User user = users.findByEmailIgnoreCase(request.email().trim()).orElse(null);
        if (user == null || !Boolean.TRUE.equals(user.getActive()) || user.getPasswordHash() == null) {
            throw new InvalidCredentialsException();
        }
        if (Boolean.TRUE.equals(user.getAccountLocked())) {
            throw new AccountLockedException();
        }
        if (!passwords.matches(request.password(), user.getPasswordHash())) {
            int attempts = (user.getFailedLoginAttempts() == null ? 0 : user.getFailedLoginAttempts()) + 1;
            user.setFailedLoginAttempts(attempts);
            if (attempts >= maximumAttempts) user.setAccountLocked(true);
            users.save(user);
            throw new InvalidCredentialsException();
        }
        user.setFailedLoginAttempts(0);
        user.setLastLoginAt(LocalDateTime.now());
        users.save(user);
        JwtService.IssuedToken token = jwtService.issue(user);
        return new LoginResponse(token.value(), "Bearer", token.expiresAt(), toSession(user));
    }

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (users.findByEmailIgnoreCase(email).isPresent()) {
            throw new DuplicateAccountException();
        }

        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPasswordHash(passwords.encode(request.password()));
        user.setRole("SALES_EXECUTIVE");
        user.setActive(true);
        user.setAccountLocked(false);
        user.setFailedLoginAttempts(0);
        user.setLastLoginAt(LocalDateTime.now());
        user = users.saveAndFlush(user);

        JwtService.IssuedToken token = jwtService.issue(user);
        return new LoginResponse(token.value(), "Bearer", token.expiresAt(), toSession(user));
    }

    @Transactional(readOnly = true)
    public UserSessionResponse me() {
        return toSession(currentUser());
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = currentUser();
        if (!passwords.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        if (passwords.matches(request.newPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("New password must be different from the current password.");
        }
        user.setPasswordHash(passwords.encode(request.newPassword()));
        users.save(user);
    }

    private User currentUser() {
        return users.findById(currentUserService.getCurrentUserId())
                .filter(user -> Boolean.TRUE.equals(user.getActive()))
                .orElseThrow(() -> new InvalidCredentialsException());
    }

    private UserSessionResponse toSession(User user) {
        return new UserSessionResponse(user.getUserId(), user.getFullName(), user.getEmail(),
                user.getRole(), user.getDesignation(), user.getAvatar());
    }

    public static class InvalidCredentialsException extends RuntimeException {}
    public static class AccountLockedException extends RuntimeException {}
    public static class DuplicateAccountException extends RuntimeException {}
}
