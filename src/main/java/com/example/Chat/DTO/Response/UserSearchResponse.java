package com.example.Chat.DTO.Response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserSearchResponse {

    private Long userId;
    private String fullName;
    private String email;
    private String designation;
    private String role;
    private String avatar;
}