package com.example.Email.DTO.Request;
import jakarta.validation.constraints.*;
public record EmailOrganizerRequest(@NotBlank @Size(max=80) String name, @NotBlank @Pattern(regexp="^#[0-9A-Fa-f]{6}$") String color) {}
