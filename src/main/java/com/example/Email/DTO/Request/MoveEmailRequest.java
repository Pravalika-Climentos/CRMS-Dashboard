package com.example.Email.DTO.Request;
import jakarta.validation.constraints.PositiveOrZero;
public record MoveEmailRequest(Long folderId, @PositiveOrZero Long expectedVersion) {}
