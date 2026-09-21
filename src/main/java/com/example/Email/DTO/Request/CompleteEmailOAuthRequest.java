package com.example.Email.DTO.Request; import jakarta.validation.constraints.NotBlank;
public record CompleteEmailOAuthRequest(@NotBlank String code,@NotBlank String state){}
