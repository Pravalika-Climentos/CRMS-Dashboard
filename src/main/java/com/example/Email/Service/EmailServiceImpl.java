package com.example.Email.Service;

import com.example.CRM.Entity.User;
import com.example.CRM.Repository.UserRepository;
import com.example.Common.DTO.Response.PageResponse;
import com.example.Common.Exception.*;
import com.example.Common.Service.CurrentUserService;
import com.example.Email.DTO.Request.*;
import com.example.Email.DTO.Response.*;
import com.example.Email.Entity.*;
import com.example.Email.Repository.*;
import com.example.Email.Repository.Projection.EmailAttachmentCountProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmailServiceImpl implements EmailService {
    private final EmailMessageRepository messages;
    private final EmailRecipientRepository recipients;
    private final EmailMailboxEntryRepository mailbox;
    private final EmailAttachmentRepository attachments;
    private final UserRepository users;
    private final CurrentUserService currentUserService;
    private final Clock clock;
    private final EmailCrmLinkService crmLinks;

    @Value("${email.storage-root:uploads/email}")
    private String storageRoot;

    @Override
    public PageResponse<EmailSummaryResponse> list(EmailFolder folder, String search, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid pagination values.");
        String query = search == null ? "" : search.trim();
        if (query.length() > 150) throw new IllegalArgumentException("Search cannot exceed 150 characters.");
        Page<EmailMailboxEntry> result = mailbox.findMailbox(currentUserId(), folder.name(), query,
                PageRequest.of(page, size));
        return page(result);
    }

    @Override
    public PageResponse<EmailSummaryResponse> listCustomFolder(Long folderId, String search, int page, int size) {
        validatePage(page, size);
        return page(mailbox.findCustomFolder(currentUserId(), folderId, normalizeSearch(search), PageRequest.of(page, size)));
    }

    @Override
    public PageResponse<EmailSummaryResponse> listLabel(Long labelId, String search, int page, int size) {
        validatePage(page, size);
        return page(mailbox.findLabel(currentUserId(), labelId, normalizeSearch(search), PageRequest.of(page, size)));
    }

    private PageResponse<EmailSummaryResponse> page(Page<EmailMailboxEntry> result) {
        List<Long> messageIds = result.getContent().stream().map(entry -> entry.getMessage().getMessageId()).toList();
        Map<Long, Long> attachmentCounts = messageIds.isEmpty() ? Map.of() : attachments.countByMessageIds(messageIds)
                .stream().collect(Collectors.toMap(EmailAttachmentCountProjection::getMessageId,
                        EmailAttachmentCountProjection::getAttachmentCount));
        List<EmailSummaryResponse> items = result.getContent().stream()
                .map(entry -> summary(entry, attachmentCounts.getOrDefault(entry.getMessage().getMessageId(), 0L))).toList();
        return new PageResponse<>(items, result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), result.hasNext());
    }

    private void validatePage(int page, int size) { if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid pagination values."); }
    private String normalizeSearch(String search) { String query = search == null ? "" : search.trim(); if (query.length() > 150) throw new IllegalArgumentException("Search cannot exceed 150 characters."); return query; }

    @Override public EmailFolderCountsResponse counts() { return mailbox.countFolders(currentUserId()); }

    @Override
    public PageResponse<EmailUserResponse> searchRecipients(String search, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid pagination values.");
        String query = search == null ? "" : search.trim();
        Page<User> result = users.searchActiveCalendarUsers(query, currentUserId(), PageRequest.of(page, size));
        return new PageResponse<>(result.getContent().stream().map(this::user).toList(), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages(), result.hasNext());
    }

    @Override
    public EmailDetailResponse details(Long messageId) {
        return detail(accessible(messageId), currentUserId());
    }

    @Override @Transactional
    public EmailDetailResponse createDraft(EmailContentRequest request) {
        User sender = currentUser();
        EmailMessage message = new EmailMessage();
        message.setSender(sender);
        message.setThreadKey(UUID.randomUUID().toString());
        applyContent(message, request);
        message.setStatus(EmailMessageStatus.DRAFT);
        message = messages.saveAndFlush(message);
        replaceRecipients(message, request, false);
        EmailMailboxEntry entry = createMailbox(message, sender, EmailMailboxRole.SENDER, true);
        return detail(entry, sender.getUserId());
    }

    @Override @Transactional
    public EmailDetailResponse updateDraft(Long messageId, EmailContentRequest request) {
        EmailMessage message = ownedDraft(messageId);
        requireVersion(message.getVersion(), request.expectedVersion(), "EMAIL_DRAFT_VERSION_CONFLICT");
        applyContent(message, request);
        recipients.deleteByMessageMessageId(messageId);
        // Delete existing rows before inserting replacements. Without this
        // flush, Hibernate can insert an unchanged address first and violate
        // uq_email_recipient_message_address during attachment auto-save.
        recipients.flush();
        replaceRecipients(message, request, false);
        messages.flush();
        return detail(accessible(messageId), currentUserId());
    }

    @Override @Transactional
    public EmailDetailResponse send(EmailContentRequest request) {
        User sender = currentUser();
        EmailMessage message = new EmailMessage();
        message.setSender(sender);
        message.setThreadKey(UUID.randomUUID().toString());
        applyContent(message, request);
        return deliverNew(message, request, sender);
    }

    @Override @Transactional
    public EmailDetailResponse sendDraft(Long messageId, Long expectedVersion) {
        EmailMessage message = ownedDraft(messageId);
        requireVersion(message.getVersion(), expectedVersion, "EMAIL_DRAFT_VERSION_CONFLICT");
        List<EmailRecipient> currentRecipients = recipients.findAllByMessageId(messageId);
        if (currentRecipients.isEmpty()) throw new IllegalArgumentException("At least one recipient is required.");
        validateInternalRecipients(currentRecipients, message.getSender().getUserId());
        Instant now = clock.instant();
        message.setStatus(EmailMessageStatus.SENT);
        message.setSentAt(now);
        currentRecipients.forEach(recipient -> {
            recipient.setDeliveryStatus(EmailDeliveryStatus.DELIVERED);
            recipient.setDeliveredAt(now);
            createMailboxIfMissing(message, recipient.getRecipientUser(), EmailMailboxRole.RECIPIENT, false);
        });
        messages.flush();
        return detail(accessible(messageId), currentUserId());
    }

    @Override @Transactional
    public EmailDetailResponse reply(Long messageId, EmailReplyRequest request, boolean replyAll) {
        Long userId = currentUserId();
        EmailMailboxEntry originalEntry = accessible(messageId);
        EmailMessage original = originalEntry.getMessage();
        User sender = currentUser();
        LinkedHashSet<String> to = new LinkedHashSet<>();
        String originalSender = messageSender(original).email();
        if (!originalSender.equalsIgnoreCase(sender.getEmail())) to.add(originalSender);
        if (replyAll) {
            recipients.findAllByMessageId(messageId).stream()
                    .filter(r -> r.getRecipientType() != EmailRecipientType.BCC)
                    .map(EmailRecipient::getEmailAddress)
                    .filter(email -> !email.equalsIgnoreCase(sender.getEmail()))
                    .forEach(to::add);
        }
        if (to.isEmpty()) throw new IllegalArgumentException("Reply has no recipient.");
        EmailContentRequest content = new EmailContentRequest(List.copyOf(to), request.cc(), request.bcc(),
                replySubject(original.getSubject()), request.body(), null);
        EmailMessage reply = new EmailMessage();
        reply.setSender(sender); reply.setThreadKey(original.getThreadKey()); reply.setParentMessage(original);
        applyContent(reply, content);
        return deliverNew(reply, content, sender);
    }

    @Override @Transactional
    public EmailDetailResponse forward(Long messageId, EmailForwardRequest request) {
        EmailMailboxEntry originalEntry = accessible(messageId);
        EmailMessage original = originalEntry.getMessage();
        String note = request.note() == null ? "" : request.note().trim();
        String body = (note.isBlank() ? "" : note + "\n\n") + "---------- Forwarded message ----------\n"
                + "From: " + messageSender(original).fullName() + " <" + messageSender(original).email() + ">\n"
                + "Subject: " + original.getSubject() + "\n\n" + original.getBody();
        EmailContentRequest content = new EmailContentRequest(request.to(), request.cc(), request.bcc(),
                forwardSubject(original.getSubject()), body, null);
        EmailMessage forwarded = new EmailMessage();
        forwarded.setSender(currentUser()); forwarded.setThreadKey(UUID.randomUUID().toString());
        forwarded.setParentMessage(original); applyContent(forwarded, content);
        return deliverNew(forwarded, content, forwarded.getSender());
    }

    @Override @Transactional
    public EmailDetailResponse updateState(Long messageId, UpdateEmailStateRequest request) {
        EmailMailboxEntry entry = mailbox.findByIdForUpdate(new EmailMailboxEntryId(messageId, currentUserId()))
                .orElseThrow(() -> new ResourceNotFoundException("Email was not found."));
        requireVersion(entry.getVersion(), request.expectedVersion(), "EMAIL_STATE_VERSION_CONFLICT");
        if (request.read() == null && request.starred() == null && request.important() == null
                && request.archived() == null && request.spam() == null && request.trashed() == null)
            throw new IllegalArgumentException("At least one mailbox state field is required.");
        if (request.read() != null) entry.setRead(request.read());
        if (request.starred() != null) entry.setStarred(request.starred());
        if (request.important() != null) entry.setImportant(request.important());
        if (request.archived() != null) entry.setArchived(request.archived());
        if (request.spam() != null) { entry.setSpam(request.spam()); if (request.spam()) entry.setArchived(false); }
        if (request.trashed() != null) entry.setTrashedAt(request.trashed() ? clock.instant() : null);
        mailbox.flush();
        return detail(entry, currentUserId());
    }

    @Override @Transactional
    public EmailAttachmentResponse addAttachment(Long messageId, MultipartFile file) {
        EmailMessage message = ownedDraft(messageId);
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Attachment file is required.");
        if (file.getSize() > 10L * 1024 * 1024) throw new IllegalArgumentException("Attachment cannot exceed 10 MB.");
        String original = file.getOriginalFilename();
        if (original == null || original.isBlank()) original = "attachment";
        original = Paths.get(original).getFileName().toString();
        if (original.length() > 255) throw new IllegalArgumentException("Attachment filename cannot exceed 255 characters.");
        String stored = UUID.randomUUID().toString();
        Path root = Paths.get(storageRoot).toAbsolutePath().normalize();
        Path directory = root.resolve(String.valueOf(messageId)).normalize();
        Path destination = directory.resolve(stored).normalize();
        if (!destination.startsWith(directory)) throw new IllegalArgumentException("Invalid attachment filename.");
        try {
            Files.createDirectories(directory);
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
            EmailAttachment attachment = new EmailAttachment();
            attachment.setMessage(message); attachment.setUploadedBy(currentUser());
            attachment.setOriginalFilename(original); attachment.setStoredFilename(stored);
            attachment.setContentType(file.getContentType() == null ? "application/octet-stream" : file.getContentType());
            attachment.setFileSize(file.getSize()); attachment.setStoragePath(destination.toString());
            attachment = attachments.save(attachment);
            return new EmailAttachmentResponse(attachment.getAttachmentId(), original, attachment.getContentType(), attachment.getFileSize());
        } catch (IOException exception) {
            try { Files.deleteIfExists(destination); } catch (IOException ignored) { }
            throw new IllegalStateException("Unable to store email attachment.", exception);
        }
    }

    @Override
    public EmailAttachmentDownload downloadAttachment(Long messageId, Long attachmentId) {
        accessible(messageId);
        EmailAttachment attachment = attachments.findByAttachmentIdAndMessageMessageId(attachmentId, messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Email attachment was not found."));
        Path path = Paths.get(attachment.getStoragePath()).toAbsolutePath().normalize();
        Path root = Paths.get(storageRoot).toAbsolutePath().normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path))
            throw new ResourceNotFoundException("Email attachment file was not found.");
        return new EmailAttachmentDownload(path, attachment.getOriginalFilename(), attachment.getContentType());
    }

    @Override @Transactional
    public void deleteAttachment(Long messageId, Long attachmentId) {
        ownedDraft(messageId);
        EmailAttachment attachment = attachments.findByAttachmentIdAndMessageMessageId(attachmentId, messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Email attachment was not found."));
        Path path = Paths.get(attachment.getStoragePath()).toAbsolutePath().normalize();
        attachments.delete(attachment);
        try { Files.deleteIfExists(path); }
        catch (IOException exception) { throw new IllegalStateException("Unable to delete email attachment file.", exception); }
    }

    @Override @Transactional
    public void permanentlyDelete(Long messageId) {
        EmailMailboxEntry entry = mailbox.findByIdForUpdate(new EmailMailboxEntryId(messageId, currentUserId()))
                .orElseThrow(() -> new ResourceNotFoundException("Email was not found."));
        if (entry.getTrashedAt() == null) throw new ConflictException("EMAIL_NOT_IN_TRASH", "Move the email to trash before deleting it permanently.");
        mailbox.delete(entry); mailbox.flush();
        if (mailbox.countByMessageMessageId(messageId) == 0) {
            List<Path> files = attachments.findByMessageMessageIdOrderByAttachmentId(messageId).stream()
                    .map(EmailAttachment::getStoragePath).map(Paths::get).toList();
            messages.deleteById(messageId);
            messages.flush();
            files.forEach(path -> {
                try { Files.deleteIfExists(path); }
                catch (IOException exception) { throw new IllegalStateException("Unable to delete email attachment file.", exception); }
            });
        }
    }

    private EmailDetailResponse deliverNew(EmailMessage message, EmailContentRequest request, User sender) {
        List<ResolvedRecipient> resolved = resolveRecipients(request, true, sender.getUserId());
        message.setStatus(EmailMessageStatus.SENT); message.setSentAt(clock.instant());
        message = messages.saveAndFlush(message);
        persistRecipients(message, resolved, true);
        crmLinks.autoLink(message, resolved.stream().map(ResolvedRecipient::email).toList());
        createMailbox(message, sender, EmailMailboxRole.SENDER, true);
        for (ResolvedRecipient recipient : resolved)
            createMailboxIfMissing(message, recipient.user(), EmailMailboxRole.RECIPIENT, false);
        return detail(accessible(message.getMessageId()), sender.getUserId());
    }

    private void replaceRecipients(EmailMessage message, EmailContentRequest request, boolean sending) {
        persistRecipients(message, resolveRecipients(request, sending, message.getSender().getUserId()), sending);
    }

    private void persistRecipients(EmailMessage message, List<ResolvedRecipient> values, boolean delivered) {
        Instant now = clock.instant();
        List<EmailRecipient> entities = values.stream().map(value -> {
            EmailRecipient recipient = new EmailRecipient();
            recipient.setMessage(message); recipient.setRecipientUser(value.user());
            recipient.setEmailAddress(value.email()); recipient.setRecipientType(value.type());
            recipient.setDeliveryStatus(delivered ? EmailDeliveryStatus.DELIVERED : EmailDeliveryStatus.PENDING);
            recipient.setDeliveredAt(delivered ? now : null);
            return recipient;
        }).toList();
        recipients.saveAll(entities);
    }

    private List<ResolvedRecipient> resolveRecipients(EmailContentRequest request, boolean required, Long senderId) {
        LinkedHashMap<String, EmailRecipientType> addresses = new LinkedHashMap<>();
        addAddresses(addresses, request.to(), EmailRecipientType.TO);
        addAddresses(addresses, request.cc(), EmailRecipientType.CC);
        addAddresses(addresses, request.bcc(), EmailRecipientType.BCC);
        if (required && addresses.isEmpty()) throw new IllegalArgumentException("At least one recipient is required.");
        return addresses.entrySet().stream().map(entry -> {
            User user = users.findByEmailIgnoreCase(entry.getKey()).filter(u -> Boolean.TRUE.equals(u.getActive()))
                    .orElseThrow(() -> new IllegalArgumentException("External delivery is not configured. Recipient must be an active CRM user: " + entry.getKey()));
            if (user.getUserId().equals(senderId)) throw new IllegalArgumentException("You cannot send an email to yourself.");
            return new ResolvedRecipient(entry.getKey(), entry.getValue(), user);
        }).toList();
    }

    private void validateInternalRecipients(List<EmailRecipient> values, Long senderId) {
        for (EmailRecipient recipient : values) {
            User user = recipient.getRecipientUser();
            if (user == null || !Boolean.TRUE.equals(user.getActive()))
                throw new IllegalArgumentException("All recipients must be active CRM users.");
            if (user.getUserId().equals(senderId)) throw new IllegalArgumentException("You cannot send an email to yourself.");
        }
    }

    private void addAddresses(Map<String, EmailRecipientType> target, List<String> values, EmailRecipientType type) {
        if (values == null) return;
        for (String raw : values) {
            if (raw == null || raw.isBlank()) continue;
            String email = raw.trim().toLowerCase(Locale.ROOT);
            if (target.putIfAbsent(email, type) != null)
                throw new IllegalArgumentException("Recipient appears more than once: " + email);
        }
    }

    private void applyContent(EmailMessage message, EmailContentRequest request) {
        message.setSubject(request.subject() == null ? "" : request.subject().trim());
        message.setBody(request.body() == null ? "" : request.body().trim());
    }

    private EmailMessage ownedDraft(Long messageId) {
        EmailMessage message = messages.findByIdForUpdate(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Draft was not found."));
        if (!message.getSender().getUserId().equals(currentUserId())) throw new ForbiddenOperationException("Only the draft owner can modify it.");
        if (message.getStatus() != EmailMessageStatus.DRAFT) throw new ConflictException("EMAIL_NOT_DRAFT", "The email is no longer a draft.");
        return message;
    }

    private EmailMailboxEntry accessible(Long messageId) {
        return mailbox.findDetailed(new EmailMailboxEntryId(messageId, currentUserId()))
                .orElseThrow(() -> new ResourceNotFoundException("Email was not found."));
    }

    private EmailMailboxEntry createMailbox(EmailMessage message, User user, EmailMailboxRole role, boolean read) {
        EmailMailboxEntry entry = new EmailMailboxEntry();
        entry.setId(new EmailMailboxEntryId(message.getMessageId(), user.getUserId()));
        entry.setMessage(message); entry.setUser(user); entry.setMailboxRole(role); entry.setRead(read);
        return mailbox.save(entry);
    }

    private void createMailboxIfMissing(EmailMessage message, User user, EmailMailboxRole role, boolean read) {
        EmailMailboxEntryId id = new EmailMailboxEntryId(message.getMessageId(), user.getUserId());
        if (!mailbox.existsById(id)) createMailbox(message, user, role, read);
    }

    private EmailSummaryResponse summary(EmailMailboxEntry entry, long attachmentCount) {
        EmailMessage message = entry.getMessage();
        String body = message.getBody().replaceAll("\\s+", " ").trim();
        String preview = body.length() <= 160 ? body : body.substring(0, 157) + "...";
        return new EmailSummaryResponse(message.getMessageId(), message.getThreadKey(), messageSender(message),
                message.getSubject(), preview, message.getStatus(), entry.getMailboxRole(), message.getSentAt(),
                yes(entry.getRead()), yes(entry.getStarred()), yes(entry.getImportant()), yes(entry.getArchived()),
                yes(entry.getSpam()), entry.getTrashedAt(), attachmentCount,
                message.getVersion(), entry.getVersion());
    }

    private EmailDetailResponse detail(EmailMailboxEntry entry, Long viewerId) {
        EmailMessage message = entry.getMessage();
        boolean senderViewing = message.getSender().getUserId().equals(viewerId)
                && (message.getExternalSenderEmail() == null
                || message.getExternalSenderEmail().equalsIgnoreCase(message.getSender().getEmail()));
        List<EmailRecipientResponse> recipientResponses = recipients.findAllByMessageId(message.getMessageId()).stream()
                .filter(r -> senderViewing || r.getRecipientType() != EmailRecipientType.BCC
                        || (r.getRecipientUser() != null && r.getRecipientUser().getUserId().equals(viewerId)))
                .map(r -> new EmailRecipientResponse(r.getRecipientId(), r.getEmailAddress(), r.getRecipientType(),
                        r.getDeliveryStatus(), r.getRecipientUser() == null ? null : user(r.getRecipientUser())))
                .toList();
        List<EmailAttachmentResponse> attachmentResponses = attachments.findByMessageMessageIdOrderByAttachmentId(message.getMessageId())
                .stream().map(a -> new EmailAttachmentResponse(a.getAttachmentId(), a.getOriginalFilename(), a.getContentType(), a.getFileSize())).toList();
        return new EmailDetailResponse(message.getMessageId(), message.getThreadKey(),
                message.getParentMessage() == null ? null : message.getParentMessage().getMessageId(), messageSender(message),
                recipientResponses, message.getSubject(), message.getBody(), message.getStatus(), entry.getMailboxRole(),
                message.getSentAt(), yes(entry.getRead()), yes(entry.getStarred()), yes(entry.getImportant()),
                yes(entry.getArchived()), yes(entry.getSpam()), entry.getTrashedAt(), attachmentResponses,
                message.getVersion(), entry.getVersion(), message.getCreatedAt(), message.getUpdatedAt());
    }

    private EmailUserResponse user(User user) { return new EmailUserResponse(user.getUserId(), user.getFullName(), user.getEmail(), user.getAvatar()); }
    private EmailUserResponse messageSender(EmailMessage message) {
        if (message.getExternalSenderEmail() == null || message.getExternalSenderEmail().isBlank()) return user(message.getSender());
        String name = message.getExternalSenderName();
        if (name == null || name.isBlank()) name = message.getExternalSenderEmail();
        return new EmailUserResponse(null, name, message.getExternalSenderEmail(), null);
    }
    private User currentUser() { return users.findById(currentUserId()).filter(u -> Boolean.TRUE.equals(u.getActive()))
            .orElseThrow(() -> new ResourceNotFoundException("Current user was not found or is inactive.")); }
    private Long currentUserId() { return currentUserService.getCurrentUserId(); }
    private boolean yes(Boolean value) { return Boolean.TRUE.equals(value); }
    private void requireVersion(Long actual, Long expected, String code) {
        if (expected == null || !Objects.equals(actual, expected))
            throw new ConflictException(code, "The email changed elsewhere. Refresh and try again.");
    }
    private String replySubject(String subject) { return subject.regionMatches(true, 0, "Re:", 0, 3) ? subject : "Re: " + subject; }
    private String forwardSubject(String subject) { return subject.regionMatches(true, 0, "Fwd:", 0, 4) ? subject : "Fwd: " + subject; }
    private record ResolvedRecipient(String email, EmailRecipientType type, User user) {}
}
