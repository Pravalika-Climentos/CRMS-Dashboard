package com.example.Call.DTO.Realtime;

import java.util.Map;

public record CallOfferEvent(
        String callId,
        String from,
        Long callerUserId,
        String callerName,
        String callerEmail,
        String callerPhone,
        String avatar,
        String type,
        Map<String, Object> offer
) {
}