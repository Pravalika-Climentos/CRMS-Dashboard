package com.example.Chat.DTO.Response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SenderResponse {

    private Long userId;
    private String fullName;
    private String avatar;
}