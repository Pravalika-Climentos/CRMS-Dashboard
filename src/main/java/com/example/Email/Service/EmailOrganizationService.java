package com.example.Email.Service;
import com.example.CRM.Entity.User;
import com.example.CRM.Repository.UserRepository;
import com.example.Common.Exception.*;
import com.example.Common.Service.CurrentUserService;
import com.example.Email.DTO.Request.*;
import com.example.Email.DTO.Response.EmailOrganizerResponse;
import com.example.Email.Entity.*;
import com.example.Email.Repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class EmailOrganizationService {
 private final EmailLabelRepository labels; private final EmailCustomFolderRepository folders;
 private final EmailMessageLabelRepository mappings; private final EmailMailboxEntryRepository mailbox;
 private final UserRepository users; private final CurrentUserService current;
 public List<EmailOrganizerResponse> labels(){return labels.findByUserUserIdOrderByNameAsc(uid()).stream().map(x->new EmailOrganizerResponse(x.getLabelId(),x.getName(),x.getColor(),mappings.countByLabelLabelId(x.getLabelId()))).toList();}
 public List<EmailOrganizerResponse> folders(){return folders.findByUserUserIdOrderByDisplayOrderAscNameAsc(uid()).stream().map(x->new EmailOrganizerResponse(x.getFolderId(),x.getName(),x.getColor(),mailbox.countByCustomFolderFolderId(x.getFolderId()))).toList();}
 @Transactional public EmailOrganizerResponse createLabel(EmailOrganizerRequest r){Long u=uid();String n=name(r.name());if(labels.existsByUserUserIdAndNameIgnoreCase(u,n))throw new ConflictException("EMAIL_LABEL_EXISTS","A label with that name already exists.");EmailLabel x=new EmailLabel();x.setUser(user());x.setName(n);x.setColor(r.color());x=labels.save(x);return new EmailOrganizerResponse(x.getLabelId(),n,x.getColor(),0);}
 @Transactional public EmailOrganizerResponse createFolder(EmailOrganizerRequest r){Long u=uid();String n=name(r.name());if(folders.existsByUserUserIdAndNameIgnoreCase(u,n))throw new ConflictException("EMAIL_FOLDER_EXISTS","A folder with that name already exists.");EmailCustomFolder x=new EmailCustomFolder();x.setUser(user());x.setName(n);x.setColor(r.color());x.setDisplayOrder(folders.findByUserUserIdOrderByDisplayOrderAscNameAsc(u).size());x=folders.save(x);return new EmailOrganizerResponse(x.getFolderId(),n,x.getColor(),0);}
 @Transactional public void addLabel(Long messageId,Long labelId){Long u=uid();EmailMailboxEntry e=entry(messageId,u);EmailLabel l=labels.findByLabelIdAndUserUserId(labelId,u).orElseThrow(()->new ResourceNotFoundException("Label not found."));if(!mappings.existsByMessageMessageIdAndUserIdAndLabelLabelId(messageId,u,labelId)){EmailMessageLabel m=new EmailMessageLabel();m.setMessage(e.getMessage());m.setUserId(u);m.setLabel(l);mappings.save(m);}}
 @Transactional public void removeLabel(Long messageId,Long labelId){mappings.deleteByMessageMessageIdAndUserIdAndLabelLabelId(messageId,uid(),labelId);}
 @Transactional public void move(Long messageId,MoveEmailRequest r){Long u=uid();EmailMailboxEntry e=entry(messageId,u);if(r.expectedVersion()!=null&&!Objects.equals(e.getVersion(),r.expectedVersion()))throw new ConflictException("EMAIL_STATE_VERSION_CONFLICT","The email changed elsewhere. Refresh and try again.");e.setCustomFolder(r.folderId()==null?null:folders.findByFolderIdAndUserUserId(r.folderId(),u).orElseThrow(()->new ResourceNotFoundException("Folder not found.")));}
 @Transactional public void deleteLabel(Long id){EmailLabel x=labels.findByLabelIdAndUserUserId(id,uid()).orElseThrow(()->new ResourceNotFoundException("Label not found."));mappings.deleteAllForLabel(id);labels.delete(x);}
 @Transactional public void deleteFolder(Long id){EmailCustomFolder x=folders.findByFolderIdAndUserUserId(id,uid()).orElseThrow(()->new ResourceNotFoundException("Folder not found."));mailbox.findByUserUserIdAndCustomFolderFolderIdOrderByUpdatedAtDesc(uid(),id).forEach(e->e.setCustomFolder(null));folders.delete(x);}
 private EmailMailboxEntry entry(Long m,Long u){return mailbox.findDetailed(new EmailMailboxEntryId(m,u)).orElseThrow(()->new ResourceNotFoundException("Email not found."));}
 private Long uid(){return current.getCurrentUserId();} private User user(){return users.findById(uid()).orElseThrow(()->new ResourceNotFoundException("Current user not found."));}
 private String name(String n){return n.trim().replaceAll("\\s+"," ");}
}
