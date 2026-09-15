package com.example.Call.DTO.Response;

import java.util.List;

public record CallListResponse(
        List<CallResponse> calls
) {
}