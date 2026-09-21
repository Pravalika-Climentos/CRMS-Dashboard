package com.example.Email.Service;

import com.example.CRM.Entity.User;
import com.example.Email.Entity.*;
import com.example.Email.Repository.EmailMailboxEntryRepository;
import com.example.Email.Repository.EmailMessageRepository;
import com.example.Email.Repository.EmailRecipientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InternalEmailNotificationService {

    private final EmailMessageRepository messages;
    private final EmailRecipientRepository recipients;
    private final EmailMailboxEntryRepository mailbox;
    private final GmailDeliveryService gmailDelivery;

    /**
     * Creates the CRM mailbox notification and, when the sender has connected
     * Gmail, delivers the same notification to each recipient's real address.
     */
    @Transactional
    public void send(User sender, Collection<User> targets, String subject, String body) {
        List<User> uniqueTargets = targets.stream()
                .filter(Objects::nonNull)
                .filter(user -> Boolean.TRUE.equals(user.getActive()))
                .filter(user -> !user.getUserId().equals(sender.getUserId()))
                .collect(Collectors.toMap(
                        User::getUserId,
                        user -> user,
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .toList();

        if (uniqueTargets.isEmpty()) return;

        Instant now = Instant.now();
        EmailMessage message = new EmailMessage();
        message.setSender(sender);
        message.setThreadKey(UUID.randomUUID().toString());
        message.setSubject(subject);
        message.setBody(body);
        message.setStatus(EmailMessageStatus.SENT);
        message.setSentAt(now);
        message = messages.saveAndFlush(message);

        EmailMessage persistedMessage = message;
        List<EmailRecipient> persistedRecipients = recipients.saveAll(
                uniqueTargets.stream()
                        .map(user -> recipient(persistedMessage, user))
                        .toList()
        );
        recipients.flush();

        if (gmailDelivery.hasConnectedAccount(sender.getUserId())) {
            GmailDeliveryService.DeliveryResult delivery = gmailDelivery.send(
                    new GmailDeliveryService.UserMail(sender.getUserId()),
                    message,
                    persistedRecipients.stream()
                            .map(recipient -> new GmailDeliveryService.RecipientMail(
                                    recipient.getEmailAddress(), recipient.getRecipientType()))
                            .toList(),
                    List.of()
            );
            message.setAccount(delivery.account());
            message.setProviderMessageId(delivery.providerMessageId());
            message.setProviderThreadId(delivery.providerThreadId());
        }

        for (EmailRecipient recipient : persistedRecipients) {
            recipient.setDeliveryStatus(EmailDeliveryStatus.DELIVERED);
            recipient.setDeliveredAt(now);
            entry(message, recipient.getRecipientUser(), EmailMailboxRole.RECIPIENT, false);
        }
        entry(message, sender, EmailMailboxRole.SENDER, true);
    }

    private EmailRecipient recipient(EmailMessage message, User user) {
        EmailRecipient recipient = new EmailRecipient();
        recipient.setMessage(message);
        recipient.setRecipientUser(user);
        recipient.setEmailAddress(user.getEmail().trim().toLowerCase(Locale.ROOT));
        recipient.setRecipientType(EmailRecipientType.TO);
        recipient.setDeliveryStatus(EmailDeliveryStatus.PENDING);
        return recipient;
    }

    private void entry(EmailMessage message, User user, EmailMailboxRole role, boolean read) {
        EmailMailboxEntry entry = new EmailMailboxEntry();
        entry.setId(new EmailMailboxEntryId(message.getMessageId(), user.getUserId()));
        entry.setMessage(message);
        entry.setUser(user);
        entry.setMailboxRole(role);
        entry.setRead(read);
        mailbox.save(entry);
    }
}
