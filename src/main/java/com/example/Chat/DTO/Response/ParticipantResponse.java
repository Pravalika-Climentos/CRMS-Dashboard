package com.example.Chat.DTO.Response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ParticipantResponse {

    private Long userId;
    private String fullName;
    private String avatar;
    private String designation;
}