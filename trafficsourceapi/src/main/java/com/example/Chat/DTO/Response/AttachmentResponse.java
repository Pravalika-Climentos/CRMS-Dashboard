package com.example.Chat.DTO.Response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AttachmentResponse {

    private Long attachmentId;
    private String fileName;
    private String filePath;
    private String mimeType;
    private Long fileSize;
}