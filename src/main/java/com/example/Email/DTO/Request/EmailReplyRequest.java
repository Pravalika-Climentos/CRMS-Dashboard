package com.example.Email.DTO.Request;

import jakarta.validation.constraints.*;
import java.util.List;

public record EmailReplyRequest(
    @NotBlank @Size(max = 2_000_000) String body,
    List<@Email @Size(max = 254) String> cc,
    List<@Email @Size(max = 254) String> bcc,
    Long accountId
) {}
