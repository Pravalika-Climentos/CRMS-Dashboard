package com.example.Email.DTO.Request;

import jakarta.validation.constraints.*;
import java.util.List;

public record EmailContentRequest(
    List<@Email @Size(max = 254) String> to,
    List<@Email @Size(max = 254) String> cc,
    List<@Email @Size(max = 254) String> bcc,
    @Size(max = 255, message = "Subject cannot exceed 255 characters.") String subject,
    @Size(max = 2_000_000, message = "Email body is too large.") String body,
    Long expectedVersion
) {}
