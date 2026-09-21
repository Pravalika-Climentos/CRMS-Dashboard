package com.example.Email.Service;

import com.example.Common.DTO.Response.PageResponse;
import com.example.Email.DTO.Request.*;
import com.example.Email.DTO.Response.*;
import com.example.Email.Entity.EmailFolder;
import org.springframework.web.multipart.MultipartFile;

public interface EmailService {
    PageResponse<EmailSummaryResponse> list(EmailFolder folder, String search, int page, int size);
    PageResponse<EmailSummaryResponse> listCustomFolder(Long folderId, String search, int page, int size);
    PageResponse<EmailSummaryResponse> listLabel(Long labelId, String search, int page, int size);
    EmailFolderCountsResponse counts();
    PageResponse<EmailUserResponse> searchRecipients(String search, int page, int size);
    EmailDetailResponse details(Long messageId);
    EmailDetailResponse createDraft(EmailContentRequest request);
    EmailDetailResponse updateDraft(Long messageId, EmailContentRequest request);
    EmailDetailResponse send(EmailContentRequest request);
    EmailDetailResponse sendDraft(Long messageId, Long expectedVersion);
    EmailDetailResponse reply(Long messageId, EmailReplyRequest request, boolean replyAll);
    EmailDetailResponse forward(Long messageId, EmailForwardRequest request);
    EmailDetailResponse updateState(Long messageId, UpdateEmailStateRequest request);
    EmailAttachmentResponse addAttachment(Long messageId, MultipartFile file);
    EmailAttachmentDownload downloadAttachment(Long messageId, Long attachmentId);
    void deleteAttachment(Long messageId, Long attachmentId);
    void permanentlyDelete(Long messageId);
}
