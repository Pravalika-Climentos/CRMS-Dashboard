package com.example.Call.DTO.Response;

import java.util.List;

public record CallRoomListResponse(
        List<CallRoomResponse> rooms
) {
}