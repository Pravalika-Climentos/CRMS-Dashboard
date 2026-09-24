package com.example.Dashboard_Data.Service;

import com.example.Common.Service.CurrentUserService;
import com.example.CRM.Entity.User;
import com.example.CRM.Repository.UserRepository;
import com.example.Email.Entity.EmailMessage;
import com.example.Email.Entity.EmailMessageStatus;
import com.example.Email.Entity.EmailRecipientType;
import com.example.Email.Service.GmailDeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class DashboardDataOtpService {
    private static final Duration OTP_LIFETIME = Duration.ofSeconds(60);
    private static final Duration ACCESS_LIFETIME = Duration.ofMinutes(15);
    private static final int MAX_ATTEMPTS = 5;

    private final CurrentUserService currentUserService;
    private final UserRepository users;
    private final GmailDeliveryService gmailDelivery;
    private final SecureRandom random = new SecureRandom();
    private final Map<Long, Challenge> challenges = new ConcurrentHashMap<>();
    private final Map<Long, AccessGrant> accessGrants = new ConcurrentHashMap<>();

    public OtpSent send(String requestedEmail) {
        User user = currentUser();
        String email = normalizeEmail(requestedEmail);
        if (!email.equals(normalizeEmail(user.getEmail()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Enter the email address registered with your CRM account.");
        }

        Instant now = Instant.now();
        Challenge existing = challenges.get(user.getUserId());
        if (existing != null && existing.resendAt().isAfter(now)) {
            long wait = Math.max(1, Duration.between(now, existing.resendAt()).toSeconds());
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Please wait " + wait + " seconds before requesting another OTP.");
        }
        if (!gmailDelivery.hasConnectedAccount(user.getUserId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Connect Gmail before requesting a Dashboard Data verification code.");
        }

        String code = "%06d".formatted(random.nextInt(1_000_000));
        Instant expiresAt = now.plus(OTP_LIFETIME);
        challenges.put(user.getUserId(), new Challenge(code, expiresAt, expiresAt, 0));
        accessGrants.remove(user.getUserId());

        EmailMessage message = new EmailMessage();
        message.setSender(user);
        message.setThreadKey(UUID.randomUUID().toString());
        message.setSubject("Your CRMS Dashboard Data verification code");
        message.setBody("Your verification code is " + code
                + ". It expires in 60 seconds. Do not share this code with anyone.");
        message.setStatus(EmailMessageStatus.SENT);
        message.setSentAt(now);
        try {
            gmailDelivery.send(new GmailDeliveryService.UserMail(user.getUserId()), message,
                    List.of(new GmailDeliveryService.RecipientMail(email, EmailRecipientType.TO)), List.of());
        } catch (RuntimeException error) {
            challenges.remove(user.getUserId());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "The verification email could not be sent. Reconnect Gmail and try again.", error);
        }
        return new OtpSent("Verification code sent to " + mask(email) + ".", 60);
    }

    public OtpVerified verify(String requestedEmail, String requestedCode) {
        User user = currentUser();
        String email = normalizeEmail(requestedEmail);
        if (!email.equals(normalizeEmail(user.getEmail()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Enter the email address registered with your CRM account.");
        }
        String code = requestedCode == null ? "" : requestedCode.trim();
        if (!code.matches("\\d{6}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter the 6-digit verification code.");
        }

        Challenge challenge = challenges.get(user.getUserId());
        Instant now = Instant.now();
        if (challenge == null || !challenge.expiresAt().isAfter(now)) {
            challenges.remove(user.getUserId());
            throw new ResponseStatusException(HttpStatus.GONE,
                    "The verification code has expired. Request a new code.");
        }
        if (challenge.attempts() >= MAX_ATTEMPTS) {
            challenges.remove(user.getUserId());
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many incorrect attempts. Request a new code.");
        }
        if (!challenge.code().equals(code)) {
            challenges.put(user.getUserId(), challenge.withAttempt());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The verification code is incorrect.");
        }

        challenges.remove(user.getUserId());
        String token = UUID.randomUUID().toString();
        Instant expiresAt = now.plus(ACCESS_LIFETIME);
        accessGrants.put(user.getUserId(), new AccessGrant(token, expiresAt));
        return new OtpVerified(token, expiresAt);
    }

    public void requireVerified(String token) {
        Long userId = currentUserService.getCurrentUserId();
        AccessGrant grant = accessGrants.get(userId);
        if (grant == null || token == null || !grant.token().equals(token) || !grant.expiresAt().isAfter(Instant.now())) {
            accessGrants.remove(userId);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Dashboard Data verification is required.");
        }
    }

    private User currentUser() {
        return users.findById(currentUserService.getCurrentUserId())
                .filter(user -> Boolean.TRUE.equals(user.getActive()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "The signed-in CRM user is unavailable."));
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return "***" + email.substring(Math.max(0, at));
        return email.substring(0, 2) + "***" + email.substring(at);
    }

    private record Challenge(String code, Instant expiresAt, Instant resendAt, int attempts) {
        Challenge withAttempt() { return new Challenge(code, expiresAt, resendAt, attempts + 1); }
    }
    private record AccessGrant(String token, Instant expiresAt) {}
    public record OtpSent(String message, int expiresInSeconds) {}
    public record OtpVerified(String verificationToken, Instant expiresAt) {}
}
