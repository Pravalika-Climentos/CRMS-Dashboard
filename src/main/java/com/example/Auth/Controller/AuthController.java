package com.example.Auth.Controller;

import com.example.Auth.DTO.*;
import com.example.Auth.Service.AuthService;
import com.example.Auth.Service.ForgotPasswordService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final ForgotPasswordService forgotPasswordService;
    public AuthController(AuthService authService, ForgotPasswordService forgotPasswordService) {
        this.authService = authService;
        this.forgotPasswordService = forgotPasswordService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) { return authService.login(request); }

    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(201).body(authService.register(request));
    }

    @PostMapping("/forgot-password/request")
    public ForgotPasswordService.ChallengeResponse forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        return forgotPasswordService.request(request.email());
    }

    @PostMapping("/forgot-password/reset")
    public Map<String, String> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        forgotPasswordService.reset(request);
        return Map.of("message", "Password reset successfully. You can now sign in.");
    }

    @GetMapping("/me")
    public UserSessionResponse me() { return authService.me(); }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() { return ResponseEntity.noContent().build(); }

    @PatchMapping("/password")
    public Map<String, String> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return Map.of("message", "Password changed successfully.");
    }
}
