package com.example.Email.Service;

import com.example.CRM.Entity.User;
import com.example.Common.Exception.ResourceNotFoundException;
import com.example.Common.Service.CurrentUserService;
import com.example.Email.Entity.*;
import com.example.Email.Repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class GmailSyncService {
    private static final String GMAIL_API = "https://gmail.googleapis.com/gmail/v1/users/me";

    private final EmailAccountRepository accounts;
    private final EmailMessageRepository messages;
    private final EmailRecipientRepository recipients;
    private final EmailMailboxEntryRepository mailbox;
    private final EmailAttachmentRepository attachments;
    private final CurrentUserService currentUser;
    private final EmailTokenCipher cipher;
    private final RestClient http = RestClient.create();

    @Value("${email.oauth.google.client-id:}") private String clientId;
    @Value("${email.oauth.google.client-secret:}") private String clientSecret;
    @Value("${email.gmail.initial-sync-limit:100}") private int initialSyncLimit;
    @Value("${email.storage-root:uploads/email}") private String storageRoot;

    @Transactional
    public int syncConnected() {
        int synchronizedMessages = 0;
        List<EmailAccount> gmailAccounts = accounts.findByUserUserIdOrderByCreatedAtAsc(currentUser.getCurrentUserId())
                .stream().filter(account -> account.getProvider() == EmailProvider.GMAIL)
                .filter(account -> account.getStatus() == EmailAccountStatus.CONNECTED
                        || account.getStatus() == EmailAccountStatus.ERROR)
                .toList();
        for (EmailAccount account : gmailAccounts) synchronizedMessages += synchronize(account);
        return synchronizedMessages;
    }

    @Scheduled(fixedDelayString = "${email.gmail.sync-interval:30s}")
    @Transactional
    public void syncAllConnectedAccounts() {
        List<EmailAccount> connected = accounts.findByProviderAndStatus(
                EmailProvider.GMAIL, EmailAccountStatus.CONNECTED);
        for (EmailAccount account : connected) {
            try {
                synchronize(account);
            } catch (RuntimeException ignored) {
                // synchronize stores the account error; continue with the other mailboxes.
            }
        }
    }

    @Transactional
    public int sync(Long accountId) {
        EmailAccount account = accounts.findByAccountIdAndUserUserId(accountId, currentUser.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Email account was not found."));
        if (account.getProvider() != EmailProvider.GMAIL)
            throw new IllegalArgumentException("This synchronization endpoint currently supports Gmail accounts only.");
        if (account.getStatus() != EmailAccountStatus.CONNECTED && account.getStatus() != EmailAccountStatus.ERROR)
            throw new IllegalStateException("Reconnect this Gmail account before synchronizing it.");
        return synchronize(account);
    }

    private int synchronize(EmailAccount account) {
        account.setStatus(EmailAccountStatus.SYNCING);
        account.setLastSyncError(null);
        accounts.save(account);
        try {
            String accessToken = validAccessToken(account);
            boolean initialSynchronization = account.getLastSyncAt() == null;
            int remaining = initialSynchronization ? Math.max(1, Math.min(initialSyncLimit, 500)) : 50;
            int synchronizedMessages = 0;
            String pageToken = null;
            String recentQuery = initialSynchronization ? null
                    : "after:" + account.getLastSyncAt().minusSeconds(90).getEpochSecond();
            do {
                int pageSize = Math.min(remaining, 100);
                String url = UriComponentsBuilder.fromUriString(GMAIL_API + "/messages")
                        .queryParam("maxResults", pageSize)
                        .queryParam("includeSpamTrash", true)
                        .queryParamIfPresent("q", Optional.ofNullable(recentQuery))
                        .queryParamIfPresent("pageToken", Optional.ofNullable(pageToken))
                        .build().encode().toUriString();
                Map<String, Object> page = get(url, accessToken);
                for (Map<String, Object> item : maps(page.get("messages"))) {
                    String providerMessageId = string(item.get("id"));
                    if (providerMessageId == null) continue;
                    Map<String, Object> gmailMessage = get(GMAIL_API + "/messages/" + providerMessageId + "?format=full", accessToken);
                    importMessage(account, gmailMessage, accessToken);
                    synchronizedMessages++;
                    remaining--;
                    if (remaining == 0) break;
                }
                pageToken = remaining == 0 ? null : string(page.get("nextPageToken"));
            } while (pageToken != null);

            Map<String, Object> profile = get(GMAIL_API + "/profile", accessToken);
            account.setSyncCursor(string(profile.get("historyId")));
            account.setLastSyncAt(Instant.now());
            account.setLastSyncError(null);
            account.setStatus(EmailAccountStatus.CONNECTED);
            accounts.save(account);
            return synchronizedMessages;
        } catch (RuntimeException exception) {
            account.setStatus(EmailAccountStatus.ERROR);
            account.setLastSyncError(truncate(exception.getMessage(), 1000));
            accounts.save(account);
            throw new IllegalStateException("Gmail synchronization failed: " + safeMessage(exception), exception);
        }
    }

    private String validAccessToken(EmailAccount account) {
        Instant expiresAt = account.getTokenExpiresAt();
        if (account.getEncryptedAccessToken() != null && expiresAt != null
                && expiresAt.isAfter(Instant.now().plusSeconds(60)))
            return cipher.decrypt(account.getEncryptedAccessToken());

        if (account.getEncryptedRefreshToken() == null)
            throw new IllegalStateException("Google did not provide a refresh token. Disconnect and reconnect Gmail.");

        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("refresh_token", cipher.decrypt(account.getEncryptedRefreshToken()));
        form.add("grant_type", "refresh_token");
        Map<String, Object> response = require(http.post().uri("https://oauth2.googleapis.com/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(Map.class));
        String accessToken = string(response.get("access_token"));
        if (accessToken == null) throw new IllegalStateException("Google token refresh returned no access token.");
        account.setEncryptedAccessToken(cipher.encrypt(accessToken));
        Number expiresIn = number(response.get("expires_in"));
        account.setTokenExpiresAt(Instant.now().plusSeconds(expiresIn == null ? 3600 : expiresIn.longValue()));
        accounts.save(account);
        return accessToken;
    }

    private void importMessage(EmailAccount account, Map<String, Object> gmail, String accessToken) {
        String providerMessageId = string(gmail.get("id"));
        if (providerMessageId == null) return;
        EmailMessage message = messages.findByAccountAccountIdAndProviderMessageId(account.getAccountId(), providerMessageId)
                .orElseGet(EmailMessage::new);
        User owner = account.getUser();
        Map<String, Object> payload = map(gmail.get("payload"));
        Map<String, String> headers = headers(payload.get("headers"));
        Set<String> labels = new HashSet<>(strings(gmail.get("labelIds")));
        Address from = address(headers.get("from"));
        Instant messageTime = instant(gmail.get("internalDate"));

        message.setSender(owner); // Required by the existing internal-email model; display uses external sender fields.
        message.setAccount(account);
        message.setProviderMessageId(providerMessageId);
        message.setProviderThreadId(string(gmail.get("threadId")));
        message.setInternetMessageId(truncate(headers.get("message-id"), 998));
        message.setThreadKey(stableThreadKey(account.getAccountId(), message.getProviderThreadId(), providerMessageId));
        message.setSubject(truncate(Optional.ofNullable(headers.get("subject")).orElse(""), 255));
        Body body = body(payload);
        message.setBody(body.plain().isBlank() ? htmlToText(body.html()) : body.plain());
        message.setHtmlBody(body.html().isBlank() ? null : body.html());
        message.setExternalSenderName(truncate(from.name(), 255));
        message.setExternalSenderEmail(truncate(from.email(), 254));
        message.setStatus(labels.contains("DRAFT") ? EmailMessageStatus.DRAFT : EmailMessageStatus.SENT);
        message.setSentAt(messageTime);
        message.setReceivedAt(messageTime);
        message = messages.saveAndFlush(message);

        recipients.deleteByMessageMessageId(message.getMessageId());
        recipients.flush();
        LinkedHashMap<String, EmailRecipientType> recipientAddresses = new LinkedHashMap<>();
        addAddresses(recipientAddresses, headers.get("to"), EmailRecipientType.TO);
        addAddresses(recipientAddresses, headers.get("cc"), EmailRecipientType.CC);
        addAddresses(recipientAddresses, headers.get("bcc"), EmailRecipientType.BCC);
        for (Map.Entry<String, EmailRecipientType> recipientValue : recipientAddresses.entrySet()) {
            EmailRecipient recipient = new EmailRecipient();
            recipient.setMessage(message);
            recipient.setEmailAddress(recipientValue.getKey());
            recipient.setRecipientType(recipientValue.getValue());
            recipient.setDeliveryStatus(EmailDeliveryStatus.DELIVERED);
            recipient.setDeliveredAt(messageTime);
            recipients.save(recipient);
        }

        EmailMailboxRole role = labels.contains("SENT") || labels.contains("DRAFT")
                || account.getEmailAddress().equalsIgnoreCase(from.email())
                ? EmailMailboxRole.SENDER : EmailMailboxRole.RECIPIENT;
        EmailMailboxEntryId mailboxId = new EmailMailboxEntryId(message.getMessageId(), owner.getUserId());
        EmailMailboxEntry entry = mailbox.findById(mailboxId).orElseGet(EmailMailboxEntry::new);
        entry.setId(mailboxId);
        entry.setMessage(message);
        entry.setUser(owner);
        entry.setMailboxRole(role);
        entry.setRead(!labels.contains("UNREAD"));
        entry.setStarred(labels.contains("STARRED"));
        entry.setImportant(labels.contains("IMPORTANT"));
        entry.setSpam(labels.contains("SPAM"));
        entry.setTrashedAt(labels.contains("TRASH") ? messageTime : null);
        entry.setArchived(role == EmailMailboxRole.RECIPIENT && !labels.contains("INBOX")
                && !labels.contains("SPAM") && !labels.contains("TRASH"));
        mailbox.save(entry);
        importAttachments(account, message, providerMessageId, payload, accessToken);
    }

    private void importAttachments(EmailAccount account, EmailMessage message, String providerMessageId,
                                   Map<String, Object> payload, String accessToken) {
        for (Map<String, Object> part : attachmentParts(payload)) {
            String filename = truncate(string(part.get("filename")), 255);
            Map<String, Object> partBody = map(part.get("body"));
            String providerAttachmentId = string(partBody.get("attachmentId"));
            String inlineData = string(partBody.get("data"));
            if ((providerAttachmentId == null || providerAttachmentId.isBlank())
                    && (inlineData == null || inlineData.isBlank())) continue;

            String identity = providerAttachmentId == null ? string(part.get("partId")) : providerAttachmentId;
            String storedFilename = "gmail-" + UUID.nameUUIDFromBytes(
                    (account.getAccountId() + ":" + providerMessageId + ":" + identity)
                            .getBytes(StandardCharsets.UTF_8));
            Optional<EmailAttachment> existing = attachments.findByMessageMessageIdAndStoredFilename(
                    message.getMessageId(), storedFilename);
            Path directory = Paths.get(storageRoot).toAbsolutePath().normalize()
                    .resolve(String.valueOf(message.getMessageId())).normalize();
            Path destination = directory.resolve(storedFilename).normalize();
            if (!destination.startsWith(directory)) throw new IllegalStateException("Invalid Gmail attachment path.");
            if (existing.isPresent() && Files.isRegularFile(destination)) continue;

            String encoded = inlineData;
            if ((encoded == null || encoded.isBlank()) && providerAttachmentId != null) {
                Map<String, Object> response = get(GMAIL_API + "/messages/" + providerMessageId
                        + "/attachments/" + providerAttachmentId, accessToken);
                encoded = string(response.get("data"));
            }
            byte[] bytes = decodeBytes(encoded);
            try {
                Files.createDirectories(directory);
                Files.write(destination, bytes);
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to store Gmail attachment " + filename + ".", exception);
            }

            EmailAttachment attachment = existing.orElseGet(EmailAttachment::new);
            attachment.setMessage(message);
            attachment.setUploadedBy(account.getUser());
            attachment.setOriginalFilename(filename == null || filename.isBlank() ? "attachment" : filename);
            attachment.setStoredFilename(storedFilename);
            String contentType = string(part.get("mimeType"));
            attachment.setContentType(contentType == null || contentType.isBlank()
                    ? "application/octet-stream" : truncate(contentType, 150));
            attachment.setFileSize((long) bytes.length);
            attachment.setStoragePath(destination.toString());
            attachments.save(attachment);
        }
    }

    private List<Map<String, Object>> attachmentParts(Map<String, Object> payload) {
        List<Map<String, Object>> result = new ArrayList<>();
        collectAttachmentParts(payload, result);
        return result;
    }

    private void collectAttachmentParts(Map<String, Object> part, List<Map<String, Object>> result) {
        String filename = string(part.get("filename"));
        if (filename != null && !filename.isBlank()) result.add(part);
        for (Map<String, Object> child : maps(part.get("parts"))) collectAttachmentParts(child, result);
    }

    private Map<String, Object> get(String url, String token) {
        return require(http.get().uri(url).headers(headers -> headers.setBearerAuth(token))
                .retrieve().body(Map.class));
    }

    private Map<String, String> headers(Object value) {
        Map<String, String> result = new HashMap<>();
        for (Map<String, Object> header : maps(value)) {
            String name = string(header.get("name"));
            if (name != null) result.put(name.toLowerCase(Locale.ROOT), string(header.get("value")));
        }
        return result;
    }

    private Body body(Map<String, Object> payload) {
        String mimeType = Optional.ofNullable(string(payload.get("mimeType"))).orElse("");
        String data = string(map(payload.get("body")).get("data"));
        String decoded = decode(data);
        String plain = mimeType.equalsIgnoreCase("text/plain") ? decoded : "";
        String html = mimeType.equalsIgnoreCase("text/html") ? decoded : "";
        for (Map<String, Object> part : maps(payload.get("parts"))) {
            Body child = body(part);
            if (plain.isBlank() && !child.plain().isBlank()) plain = child.plain();
            if (html.isBlank() && !child.html().isBlank()) html = child.html();
        }
        return new Body(plain, html);
    }

    private void addAddresses(Map<String, EmailRecipientType> target, String header, EmailRecipientType type) {
        if (header == null || header.isBlank()) return;
        for (String raw : splitAddresses(header)) {
            Address parsed = address(raw);
            if (!parsed.email().isBlank()) target.putIfAbsent(parsed.email().toLowerCase(Locale.ROOT), type);
        }
    }

    private List<String> splitAddresses(String value) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (char character : value.toCharArray()) {
            if (character == '"') quoted = !quoted;
            if (character == ',' && !quoted) { result.add(current.toString()); current.setLength(0); }
            else current.append(character);
        }
        if (!current.isEmpty()) result.add(current.toString());
        return result;
    }

    private Address address(String value) {
        if (value == null) return new Address("", "");
        String trimmed = value.trim();
        int left = trimmed.lastIndexOf('<');
        int right = trimmed.lastIndexOf('>');
        if (left >= 0 && right > left) {
            String name = trimmed.substring(0, left).trim().replaceAll("^\"|\"$", "");
            return new Address(name, trimmed.substring(left + 1, right).trim());
        }
        return new Address(trimmed, trimmed);
    }

    private String decode(String data) {
        if (data == null || data.isBlank()) return "";
        try { return new String(Base64.getUrlDecoder().decode(data), StandardCharsets.UTF_8); }
        catch (IllegalArgumentException ignored) { return ""; }
    }

    private byte[] decodeBytes(String data) {
        if (data == null || data.isBlank()) return new byte[0];
        try { return Base64.getUrlDecoder().decode(data); }
        catch (IllegalArgumentException exception) { throw new IllegalStateException("Gmail returned invalid attachment data.", exception); }
    }

    private String htmlToText(String html) {
        if (html == null) return "";
        return html.replaceAll("(?is)<(script|style).*?>.*?</\\1>", " ")
                .replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("(?i)</p>", "\n")
                .replaceAll("(?s)<[^>]+>", " ").replace("&nbsp;", " ")
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").trim();
    }

    private String stableThreadKey(Long accountId, String providerThreadId, String providerMessageId) {
        String source = "gmail:" + accountId + ":" + Optional.ofNullable(providerThreadId).orElse(providerMessageId);
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private Instant instant(Object milliseconds) {
        try { return Instant.ofEpochMilli(Long.parseLong(String.valueOf(milliseconds))); }
        catch (RuntimeException ignored) { return Instant.now(); }
    }

    @SuppressWarnings("unchecked") private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
    private List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(Map.class::isInstance).map(this::map).toList();
    }
    private List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(Objects::nonNull).map(String::valueOf).toList();
    }
    private String string(Object value) { return value == null ? null : String.valueOf(value); }
    private Number number(Object value) { return value instanceof Number number ? number : null; }
    private String truncate(String value, int maximum) {
        if (value == null) return null;
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }
    private String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }
    private Map<String, Object> require(Map<?, ?> value) {
        if (value == null) throw new IllegalStateException("Gmail returned an empty response.");
        @SuppressWarnings("unchecked") Map<String, Object> typed = (Map<String, Object>) value;
        return typed;
    }

    private record Address(String name, String email) { }
    private record Body(String plain, String html) { }
}
