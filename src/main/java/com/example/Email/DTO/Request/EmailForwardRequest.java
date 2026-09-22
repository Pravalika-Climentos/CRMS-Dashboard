package com.example.Email.DTO.Request;

import jakarta.validation.constraints.*;
import java.util.List;

public record EmailForwardRequest(
    @NotEmpty List<@Email @Size(max = 254) String> to,
    List<@Email @Size(max = 254) String> cc,
    List<@Email @Size(max = 254) String> bcc,
    @Size(max = 2_000_000) String note,
    Long accountId
) {}
