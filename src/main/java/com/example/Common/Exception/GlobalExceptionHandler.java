package com.example.Common.Exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {


    // =========================================================
    // 404 - NOT FOUND
    // =========================================================

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            NoSuchElementException ex,
            HttpServletRequest request
    ) {

        ApiErrorResponse response =
                buildError(
                        HttpStatus.NOT_FOUND,
                        ex.getMessage(),
                        request.getRequestURI(),
                        null
                );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(response);
    }


    // =========================================================
    // 400 - BUSINESS / REQUEST VALIDATION
    // =========================================================

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request
    ) {

        ApiErrorResponse response =
                buildError(
                        HttpStatus.BAD_REQUEST,
                        ex.getMessage(),
                        request.getRequestURI(),
                        null
                );

        return ResponseEntity
                .badRequest()
                .body(response);
    }


    // =========================================================
    // 400 - @VALID VALIDATION
    // =========================================================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {

        Map<String, String> validationErrors =
                new LinkedHashMap<>();

        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        validationErrors.putIfAbsent(
                                error.getField(),
                                error.getDefaultMessage()
                        )
                );

        String message = validationErrors.values().stream()
                .distinct()
                .collect(Collectors.joining(" "));

        ApiErrorResponse response =
                buildError(
                        HttpStatus.BAD_REQUEST,
                        message.isBlank() ? "Please correct the invalid information and try again." : message,
                        request.getRequestURI(),
                        validationErrors
                );

        return ResponseEntity
                .badRequest()
                .body(response);
    }


    // =========================================================
    // 413 - FILE TOO LARGE
    // =========================================================

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleFileTooLarge(
            MaxUploadSizeExceededException ex,
            HttpServletRequest request
    ) {

        ApiErrorResponse response =
                buildError(
                        HttpStatus.PAYLOAD_TOO_LARGE,
                        "Uploaded file exceeds the maximum allowed size.",
                        request.getRequestURI(),
                        null
                );

        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(response);
    }

    // =========================================================
// 403 - PERMISSION DENIED
// =========================================================

@ExceptionHandler(ForbiddenOperationException.class)
public ResponseEntity<ApiErrorResponse> handleForbidden(
        ForbiddenOperationException ex,
        HttpServletRequest request
) {
    ApiErrorResponse response =
            buildError(
                    HttpStatus.FORBIDDEN,
                    "FORBIDDEN",
                    ex.getMessage(),
                    request.getRequestURI(),
                    null
            );

    return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(response);
}


// =========================================================
// 409 - BUSINESS / VERSION CONFLICT
// =========================================================

@ExceptionHandler(ConflictException.class)
public ResponseEntity<ApiErrorResponse> handleConflict(
        ConflictException ex,
        HttpServletRequest request
) {
    ApiErrorResponse response =
            buildError(
                    HttpStatus.CONFLICT,
                    ex.getCode(),
                    ex.getMessage(),
                    request.getRequestURI(),
                    null
            );

    return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(response);
}


// =========================================================
// 400 - PATH AND QUERY PARAMETER VALIDATION
// =========================================================

@ExceptionHandler(ConstraintViolationException.class)
public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
        ConstraintViolationException ex,
        HttpServletRequest request
) {
    Map<String, String> validationErrors =
            new LinkedHashMap<>();

    ex.getConstraintViolations()
            .forEach(violation ->
                    validationErrors.put(
                            violation
                                    .getPropertyPath()
                                    .toString(),
                            violation.getMessage()
                    )
            );

    String message = validationErrors.values().stream()
            .distinct()
            .collect(Collectors.joining(" "));

    ApiErrorResponse response =
            buildError(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_FAILED",
                    message.isBlank() ? "Please correct the invalid information and try again." : message,
                    request.getRequestURI(),
                    validationErrors
            );

                return ResponseEntity
                        .badRequest()
                        .body(response);
        }

        

        @ExceptionHandler(NoResourceFoundException.class)
        public ResponseEntity<ApiErrorResponse> handleMissingStaticResource(
                NoResourceFoundException exception,
                HttpServletRequest request
        ) {
        ApiErrorResponse response =
                buildError(
                        HttpStatus.NOT_FOUND,
                        "Static resource was not found.",
                        request.getRequestURI(),
                        null
                );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(response);
        }

    // =========================================================
    // 500 - INTERNAL SERVER ERROR
    // =========================================================

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(
            Exception ex,
            HttpServletRequest request
    ) {

        /*
         * Don't expose stack traces, SQL errors or internal
         * implementation details to the frontend.
         */

        ApiErrorResponse response =
                buildError(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "An unexpected server error occurred.",
                        request.getRequestURI(),
                        null
                );
        log.error(
        "Unhandled exception for {}",
        request.getRequestURI(),
        ex
);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }


    // =========================================================
    // COMMON BUILDER
    // =========================================================

    private ApiErrorResponse buildError(
            HttpStatus status,
            String message,
            String path,
            Map<String, String> validationErrors
    ) {

        return ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(path)
                .validationErrors(validationErrors)
                .build();
    }

    private ApiErrorResponse buildError(
        HttpStatus status,
        String code,
        String message,
        String path,
        Map<String, String> validationErrors
) {
    return ApiErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(status.value())
            .error(status.getReasonPhrase())
            .code(code)
            .message(message)
            .path(path)
            .validationErrors(validationErrors)
            .build();
}

private String defaultErrorCode(
        HttpStatus status
) {
    return switch (status) {
        case BAD_REQUEST ->
                "INVALID_REQUEST";

        case NOT_FOUND ->
                "RESOURCE_NOT_FOUND";

        case PAYLOAD_TOO_LARGE ->
                "FILE_TOO_LARGE";

        case INTERNAL_SERVER_ERROR ->
                "INTERNAL_ERROR";

        default ->
                status.name();
    };
}

}
