package com.example.Auth.Service;

import com.example.Auth.DTO.ResetPasswordRequest;
import com.example.CRM.Entity.User;
import com.example.CRM.Repository.UserRepository;
import com.example.Email.Entity.EmailMessage;
import com.example.Email.Entity.EmailMessageStatus;
import com.example.Email.Entity.EmailRecipientType;
import com.example.Email.Service.GmailDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ForgotPasswordService {
    private static final Duration CODE_LIFETIME = Duration.ofMinutes(10);
    private static final Duration REQUEST_COOLDOWN = Duration.ofSeconds(60);
    private static final int MAX_ATTEMPTS = 5;
    private static final String GENERIC_MESSAGE =
            "If an active CRM account exists for this email, a verification code has been sent.";

    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final GmailDeliveryService gmailDelivery;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();
    private final Map<String, Instant> requestLimits = new ConcurrentHashMap<>();

    public ChallengeResponse request(String requestedEmail) {
        String email = normalize(requestedEmail);
        Instant now = Instant.now();
        Instant allowedAt = requestLimits.get(email);
        if (allowedAt != null && allowedAt.isAfter(now)) {
            long wait = Math.max(1, Duration.between(now, allowedAt).toSeconds());
            throw new IllegalArgumentException("Please wait " + wait + " seconds before requesting another code.");
        }
        requestLimits.put(email, now.plus(REQUEST_COOLDOWN));

        String token = UUID.randomUUID().toString();
        users.findByEmailIgnoreCase(email)
                .filter(user -> Boolean.TRUE.equals(user.getActive()))
                .filter(user -> user.getPasswordHash() != null)
                .ifPresent(user -> send(user, token, now));
        return new ChallengeResponse(token, GENERIC_MESSAGE, CODE_LIFETIME.toSeconds());
    }

    private void send(User user, String token, Instant now) {
        if (!gmailDelivery.hasConnectedAccount(user.getUserId())) {
            log.warn("Password reset email was not sent because user {} has no connected Gmail account.",
                    user.getUserId());
            return;
        }

        String code = "%06d".formatted(random.nextInt(1_000_000));
        Challenge challenge = new Challenge(user.getUserId(), digest(token, code),
                now.plus(CODE_LIFETIME), 0);
        challenges.put(token, challenge);

        EmailMessage message = new EmailMessage();
        message.setSender(user);
        message.setThreadKey(UUID.randomUUID().toString());
        message.setSubject("Your CRMS password reset code");
        message.setBody("Your CRMS password reset code is " + code
                + ". It expires in 10 minutes. If you did not request this reset, you can ignore this email.");
        message.setStatus(EmailMessageStatus.SENT);
        message.setSentAt(now);
        try {
            gmailDelivery.send(new GmailDeliveryService.UserMail(user.getUserId()), message,
                    List.of(new GmailDeliveryService.RecipientMail(user.getEmail(), EmailRecipientType.TO)),
                    List.of());
        } catch (RuntimeException error) {
            challenges.remove(token);
            log.warn("Password reset email delivery failed for user {}.", user.getUserId(), error);
        }
    }

    @Transactional
    public void reset(ResetPasswordRequest request) {
        String token = request.challengeToken().trim();
        Challenge challenge = challenges.get(token);
        Instant now = Instant.now();
        if (challenge == null || !challenge.expiresAt().isAfter(now)) {
            challenges.remove(token);
            throw new IllegalArgumentException("The verification code has expired. Request a new code.");
        }
        if (challenge.attempts() >= MAX_ATTEMPTS) {
            challenges.remove(token);
            throw new IllegalArgumentException("Too many incorrect attempts. Request a new code.");
        }
        if (!MessageDigest.isEqual(challenge.codeHash().getBytes(StandardCharsets.US_ASCII),
                digest(token, request.code().trim()).getBytes(StandardCharsets.US_ASCII))) {
            challenges.put(token, challenge.withAttempt());
            throw new IllegalArgumentException("The verification code is incorrect.");
        }

        User user = users.findById(challenge.userId())
                .filter(found -> Boolean.TRUE.equals(found.getActive()))
                .orElseThrow(() -> new IllegalArgumentException("This password reset request is no longer valid."));
        if (passwords.matches(request.newPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("New password must be different from the current password.");
        }
        user.setPasswordHash(passwords.encode(request.newPassword()));
        user.setFailedLoginAttempts(0);
        user.setAccountLocked(false);
        users.save(user);
        challenges.remove(token);
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String digest(String token, String code) {
        try {
            byte[] value = MessageDigest.getInstance("SHA-256")
                    .digest((token + ':' + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(value);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable.", impossible);
        }
    }

    private record Challenge(Long userId, String codeHash, Instant expiresAt, int attempts) {
        Challenge withAttempt() { return new Challenge(userId, codeHash, expiresAt, attempts + 1); }
    }

    public record ChallengeResponse(String challengeToken, String message, long expiresInSeconds) {}
}
