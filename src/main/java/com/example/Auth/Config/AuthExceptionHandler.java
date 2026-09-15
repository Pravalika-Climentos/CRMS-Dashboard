package com.example.Auth.Config;

import com.example.Auth.Service.AuthService;
import com.example.Common.Exception.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;

@RestControllerAdvice
public class AuthExceptionHandler {
    @ExceptionHandler(AuthService.InvalidCredentialsException.class)
    ResponseEntity<ApiErrorResponse> invalidCredentials(HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Incorrect email or password.", request);
    }

    @ExceptionHandler(AuthService.AccountLockedException.class)
    ResponseEntity<ApiErrorResponse> locked(HttpServletRequest request) {
        return error(HttpStatus.LOCKED, "ACCOUNT_LOCKED", "This account is locked. Contact an administrator.", request);
    }

    @ExceptionHandler(AuthService.DuplicateAccountException.class)
    ResponseEntity<ApiErrorResponse> duplicateAccount(HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "ACCOUNT_ALREADY_EXISTS",
                "An account already exists for this email address.", request);
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message,
                                                   HttpServletRequest request) {
        return ResponseEntity.status(status).body(ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now()).status(status.value()).error(status.getReasonPhrase())
                .code(code).message(message).path(request.getRequestURI()).build());
    }
}
