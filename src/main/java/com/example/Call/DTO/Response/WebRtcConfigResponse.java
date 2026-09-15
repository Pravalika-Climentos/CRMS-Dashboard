package com.example.Call.DTO.Response;

import java.util.List;

public record WebRtcConfigResponse(
        List<IceServerResponse> iceServers
) {
}
