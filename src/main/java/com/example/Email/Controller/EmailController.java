package com.example.Email.Controller;

import com.example.Common.DTO.Response.PageResponse;
import com.example.Email.DTO.Request.*;
import com.example.Email.DTO.Response.*;
import com.example.Email.Entity.EmailFolder;
import com.example.Email.Service.EmailService;
import com.example.Email.Service.EmailAttachmentDownload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;

@RestController
@RequestMapping("/api/emails")
@RequiredArgsConstructor
@Validated
public class EmailController {
    private final EmailService service;

    @GetMapping
    public PageResponse<EmailSummaryResponse> list(
            @RequestParam(defaultValue = "INBOX") EmailFolder folder,
            @RequestParam(required = false) Long labelId,
            @RequestParam(required = false) Long customFolderId,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        if (labelId != null && customFolderId != null) throw new IllegalArgumentException("Choose either a label or a custom folder.");
        if (labelId != null) return service.listLabel(labelId, search, page, size);
        if (customFolderId != null) return service.listCustomFolder(customFolderId, search, page, size);
        return service.list(folder, search, page, size);
    }

    @GetMapping("/counts")
    public EmailFolderCountsResponse counts() { return service.counts(); }

    @GetMapping("/recipients")
    public PageResponse<EmailUserResponse> recipients(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.searchRecipients(search, page, size);
    }

    @GetMapping("/{messageId}")
    public EmailDetailResponse details(@PathVariable @Positive Long messageId) {
        return service.details(messageId);
    }

    @PostMapping
    public ResponseEntity<EmailDetailResponse> send(@Valid @RequestBody EmailContentRequest request) {
        EmailDetailResponse response = service.send(request);
        return ResponseEntity.created(URI.create("/api/emails/" + response.messageId())).body(response);
    }

    @PostMapping("/drafts")
    public ResponseEntity<EmailDetailResponse> createDraft(@Valid @RequestBody EmailContentRequest request) {
        EmailDetailResponse response = service.createDraft(request);
        return ResponseEntity.created(URI.create("/api/emails/" + response.messageId())).body(response);
    }

    @PatchMapping("/drafts/{messageId}")
    public EmailDetailResponse updateDraft(@PathVariable @Positive Long messageId,
                                           @Valid @RequestBody EmailContentRequest request) {
        return service.updateDraft(messageId, request);
    }

    @PostMapping("/drafts/{messageId}/send")
    public EmailDetailResponse sendDraft(@PathVariable @Positive Long messageId,
                                         @RequestParam @PositiveOrZero Long expectedVersion) {
        return service.sendDraft(messageId, expectedVersion);
    }

    @PostMapping("/{messageId}/reply")
    public EmailDetailResponse reply(@PathVariable @Positive Long messageId,
                                     @Valid @RequestBody EmailReplyRequest request) {
        return service.reply(messageId, request, false);
    }

    @PostMapping("/{messageId}/reply-all")
    public EmailDetailResponse replyAll(@PathVariable @Positive Long messageId,
                                        @Valid @RequestBody EmailReplyRequest request) {
        return service.reply(messageId, request, true);
    }

    @PostMapping("/{messageId}/forward")
    public EmailDetailResponse forward(@PathVariable @Positive Long messageId,
                                       @Valid @RequestBody EmailForwardRequest request) {
        return service.forward(messageId, request);
    }

    @PatchMapping("/{messageId}/state")
    public EmailDetailResponse updateState(@PathVariable @Positive Long messageId,
                                           @Valid @RequestBody UpdateEmailStateRequest request) {
        return service.updateState(messageId, request);
    }

    @PostMapping(value = "/{messageId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EmailAttachmentResponse> addAttachment(
            @PathVariable @Positive Long messageId,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addAttachment(messageId, file));
    }

    @GetMapping("/{messageId}/attachments/{attachmentId}")
    public ResponseEntity<FileSystemResource> downloadAttachment(
            @PathVariable @Positive Long messageId,
            @PathVariable @Positive Long attachmentId) {
        EmailAttachmentDownload download = service.downloadAttachment(messageId, attachmentId);
        ContentDisposition disposition = ContentDisposition.attachment().filename(download.filename()).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(new FileSystemResource(download.path()));
    }

    @DeleteMapping("/{messageId}/attachments/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAttachment(@PathVariable @Positive Long messageId,
                                 @PathVariable @Positive Long attachmentId) {
        service.deleteAttachment(messageId, attachmentId);
    }

    @DeleteMapping("/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void permanentlyDelete(@PathVariable @Positive Long messageId) {
        service.permanentlyDelete(messageId);
    }
}
