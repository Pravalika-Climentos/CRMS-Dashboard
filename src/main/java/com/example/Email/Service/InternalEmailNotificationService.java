package com.example.Email.Service;
import com.example.CRM.Entity.User;import com.example.Email.Entity.*;import com.example.Email.Repository.*;
import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;import java.util.*;
@Service @RequiredArgsConstructor
public class InternalEmailNotificationService {
 private final EmailMessageRepository messages; private final EmailRecipientRepository recipients; private final EmailMailboxEntryRepository mailbox;
 @Transactional public void send(User sender,Collection<User> targets,String subject,String body){
  List<User> unique=targets.stream().filter(Objects::nonNull).filter(User::getActive).filter(u->!u.getUserId().equals(sender.getUserId())).collect(java.util.stream.Collectors.toMap(User::getUserId,u->u,(a,b)->a,LinkedHashMap::new)).values().stream().toList();
  if(unique.isEmpty())return; Instant now=Instant.now(); EmailMessage m=new EmailMessage();m.setSender(sender);m.setThreadKey(UUID.randomUUID().toString());m.setSubject(subject);m.setBody(body);m.setStatus(EmailMessageStatus.SENT);m.setSentAt(now);m=messages.saveAndFlush(m);
  for(User u:unique){EmailRecipient r=new EmailRecipient();r.setMessage(m);r.setRecipientUser(u);r.setEmailAddress(u.getEmail().toLowerCase(Locale.ROOT));r.setRecipientType(EmailRecipientType.TO);r.setDeliveryStatus(EmailDeliveryStatus.DELIVERED);r.setDeliveredAt(now);recipients.save(r);entry(m,u,EmailMailboxRole.RECIPIENT,false);}
  entry(m,sender,EmailMailboxRole.SENDER,true);
 }
 private void entry(EmailMessage m,User u,EmailMailboxRole role,boolean read){EmailMailboxEntry e=new EmailMailboxEntry();e.setId(new EmailMailboxEntryId(m.getMessageId(),u.getUserId()));e.setMessage(m);e.setUser(u);e.setMailboxRole(role);e.setRead(read);mailbox.save(e);}
}
