package com.example.Email.DTO.Request;

import jakarta.validation.constraints.NotNull;

public record UpdateEmailStateRequest(
    Boolean read,
    Boolean starred,
    Boolean important,
    Boolean archived,
    Boolean spam,
    Boolean trashed,
    @NotNull Long expectedVersion
) {}
