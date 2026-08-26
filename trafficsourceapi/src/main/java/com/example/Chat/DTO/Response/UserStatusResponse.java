package com.example.Chat.DTO.Response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserStatusResponse {

    private Long userId;
    private boolean active;
}