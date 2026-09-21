package com.example.Email.Service;

import com.example.Email.Entity.*;
import com.example.Email.Repository.EmailAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class GmailDeliveryService {
    private static final String SEND_URL = "https://gmail.googleapis.com/gmail/v1/users/me/messages/send";

    private final EmailAccountRepository accounts;
    private final EmailTokenCipher cipher;
    private final RestClient http = RestClient.create();

    @Value("${email.oauth.google.client-id:}") private String clientId;
    @Value("${email.oauth.google.client-secret:}") private String clientSecret;

    public boolean hasConnectedAccount(Long userId) {
        return !accounts.findByUserUserIdAndProviderAndStatus(
                userId, EmailProvider.GMAIL, EmailAccountStatus.CONNECTED).isEmpty();
    }

    public DeliveryResult send(UserMail sender, EmailMessage message, List<RecipientMail> recipients,
                               List<EmailAttachment> attachments) {
        EmailAccount account = accounts.findByUserUserIdAndProviderAndStatus(
                        sender.userId(), EmailProvider.GMAIL, EmailAccountStatus.CONNECTED)
                .stream().findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Connect a Gmail account before sending email to an external address."));

        String rawMessage = mime(account, message, recipients, attachments);
        Map<?, ?> response = http.post().uri(SEND_URL)
                .header("Authorization", "Bearer " + validAccessToken(account))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("raw", Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(rawMessage.getBytes(StandardCharsets.UTF_8))))
                .retrieve().body(Map.class);
        if (response == null || response.get("id") == null)
            throw new IllegalStateException("Gmail accepted no message identifier.");
        return new DeliveryResult(account, String.valueOf(response.get("id")),
                response.get("threadId") == null ? null : String.valueOf(response.get("threadId")));
    }

    private String mime(EmailAccount account, EmailMessage message, List<RecipientMail> recipients,
                        List<EmailAttachment> attachments) {
        StringBuilder headers = new StringBuilder();
        headers.append("From: ").append(address(account.getDisplayName(), account.getEmailAddress())).append("\r\n");
        appendRecipients(headers, "To", recipients, EmailRecipientType.TO);
        appendRecipients(headers, "Cc", recipients, EmailRecipientType.CC);
        appendRecipients(headers, "Bcc", recipients, EmailRecipientType.BCC);
        headers.append("Subject: ").append(encodedHeader(message.getSubject())).append("\r\n")
                .append("Date: ").append(DateTimeFormatter.RFC_1123_DATE_TIME
                        .format(Instant.now().atOffset(ZoneOffset.UTC))).append("\r\n")
                .append("MIME-Version: 1.0\r\n");

        if (attachments.isEmpty()) {
            return headers.append("Content-Type: text/plain; charset=UTF-8\r\n")
                    .append("Content-Transfer-Encoding: base64\r\n\r\n")
                    .append(Base64.getMimeEncoder(76, "\r\n".getBytes(StandardCharsets.US_ASCII))
                            .encodeToString(message.getBody().getBytes(StandardCharsets.UTF_8)))
                    .append("\r\n").toString();
        }

        String boundary = "crm-" + UUID.randomUUID();
        headers.append("Content-Type: multipart/mixed; boundary=\"").append(boundary).append("\"\r\n\r\n")
                .append("--").append(boundary).append("\r\n")
                .append("Content-Type: text/plain; charset=UTF-8\r\n")
                .append("Content-Transfer-Encoding: base64\r\n\r\n")
                .append(Base64.getMimeEncoder(76, "\r\n".getBytes(StandardCharsets.US_ASCII))
                        .encodeToString(message.getBody().getBytes(StandardCharsets.UTF_8))).append("\r\n");
        for (EmailAttachment attachment : attachments) {
            try {
                byte[] bytes = Files.readAllBytes(java.nio.file.Path.of(attachment.getStoragePath()));
                headers.append("--").append(boundary).append("\r\n")
                        .append("Content-Type: ").append(header(attachment.getContentType())).append("\r\n")
                        .append("Content-Disposition: attachment; filename=\"")
                        .append(filename(attachment.getOriginalFilename())).append("\"\r\n")
                        .append("Content-Transfer-Encoding: base64\r\n\r\n")
                        .append(Base64.getMimeEncoder(76, "\r\n".getBytes(StandardCharsets.US_ASCII))
                                .encodeToString(bytes)).append("\r\n");
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to read attachment "
                        + attachment.getOriginalFilename() + " for Gmail delivery.", exception);
            }
        }
        return headers.append("--").append(boundary).append("--\r\n").toString();
    }

    private void appendRecipients(StringBuilder target, String label, List<RecipientMail> recipients,
                                  EmailRecipientType type) {
        String values = recipients.stream().filter(recipient -> recipient.type() == type)
                .map(RecipientMail::email).reduce((left, right) -> left + ", " + right).orElse("");
        if (!values.isBlank()) target.append(label).append(": ").append(values).append("\r\n");
    }

    private String validAccessToken(EmailAccount account) {
        if (account.getEncryptedAccessToken() != null && account.getTokenExpiresAt() != null
                && account.getTokenExpiresAt().isAfter(Instant.now().plusSeconds(60)))
            return cipher.decrypt(account.getEncryptedAccessToken());
        if (account.getEncryptedRefreshToken() == null)
            throw new IllegalStateException("Reconnect Gmail before sending external email.");
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("refresh_token", cipher.decrypt(account.getEncryptedRefreshToken()));
        form.add("grant_type", "refresh_token");
        Map<?, ?> response = http.post().uri("https://oauth2.googleapis.com/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(Map.class);
        if (response == null || response.get("access_token") == null)
            throw new IllegalStateException("Google token refresh returned no access token.");
        String token = String.valueOf(response.get("access_token"));
        account.setEncryptedAccessToken(cipher.encrypt(token));
        long seconds = response.get("expires_in") instanceof Number number ? number.longValue() : 3600;
        account.setTokenExpiresAt(Instant.now().plusSeconds(seconds));
        accounts.save(account);
        return token;
    }

    private String address(String name, String email) {
        return name == null || name.isBlank() ? email : encodedHeader(name) + " <" + email + ">";
    }
    private String encodedHeader(String value) {
        String safe = header(value == null ? "" : value);
        return "=?UTF-8?B?" + Base64.getEncoder().encodeToString(safe.getBytes(StandardCharsets.UTF_8)) + "?=";
    }
    private String header(String value) { return value == null ? "application/octet-stream" : value.replace("\r", "").replace("\n", ""); }
    private String filename(String value) { return header(value).replace("\\", "_").replace("\"", "_"); }

    public record UserMail(Long userId) {}
    public record RecipientMail(String email, EmailRecipientType type) {}
    public record DeliveryResult(EmailAccount account, String providerMessageId, String providerThreadId) {}
}
