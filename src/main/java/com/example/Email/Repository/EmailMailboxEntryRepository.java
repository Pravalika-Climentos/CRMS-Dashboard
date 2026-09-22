package com.example.Email.Repository;

import com.example.Email.DTO.Response.EmailFolderCountsResponse;
import com.example.Email.Entity.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.List;

public interface EmailMailboxEntryRepository extends JpaRepository<EmailMailboxEntry, EmailMailboxEntryId> {
    long countByMessageMessageId(Long messageId);
    long countByCustomFolderFolderId(Long folderId);
    List<EmailMailboxEntry> findByUserUserIdAndCustomFolderFolderIdOrderByUpdatedAtDesc(Long userId, Long folderId);
    @Query(value = """
        select e from EmailMailboxEntry e
        join fetch e.message m
        join fetch m.sender
        where e.user.userId = :userId
          and (m.account is null or m.account.status <> com.example.Email.Entity.EmailAccountStatus.DISCONNECTED)
          and (
            (:folder = 'INBOX' and e.mailboxRole = com.example.Email.Entity.EmailMailboxRole.RECIPIENT and m.status = com.example.Email.Entity.EmailMessageStatus.SENT and e.archived = false and e.spam = false and e.trashedAt is null)
            or (:folder = 'SENT' and e.mailboxRole = com.example.Email.Entity.EmailMailboxRole.SENDER and m.status = com.example.Email.Entity.EmailMessageStatus.SENT and e.trashedAt is null)
            or (:folder = 'DRAFTS' and e.mailboxRole = com.example.Email.Entity.EmailMailboxRole.SENDER and m.status = com.example.Email.Entity.EmailMessageStatus.DRAFT and e.trashedAt is null)
            or (:folder = 'STARRED' and e.starred = true and e.trashedAt is null)
            or (:folder = 'IMPORTANT' and e.important = true and e.trashedAt is null)
            or (:folder = 'ARCHIVE' and e.archived = true and e.trashedAt is null)
            or (:folder = 'SPAM' and e.spam = true and e.trashedAt is null)
            or (:folder = 'TRASH' and e.trashedAt is not null)
          )
          and (:accountId is null or m.account.accountId = :accountId)
          and (:unreadOnly = false or e.read = false)
          and (:search = '' or lower(m.subject) like lower(concat('%', :search, '%'))
               or lower(m.body) like lower(concat('%', :search, '%'))
               or lower(m.sender.fullName) like lower(concat('%', :search, '%'))
               or lower(m.sender.email) like lower(concat('%', :search, '%'))
               or lower(coalesce(m.externalSenderName, '')) like lower(concat('%', :search, '%'))
               or lower(coalesce(m.externalSenderEmail, '')) like lower(concat('%', :search, '%')))
        order by coalesce(m.sentAt, m.createdAt) desc, m.messageId desc
        """,
        countQuery = """
        select count(e) from EmailMailboxEntry e join e.message m
        where e.user.userId = :userId
          and (m.account is null or m.account.status <> com.example.Email.Entity.EmailAccountStatus.DISCONNECTED)
          and (
            (:folder = 'INBOX' and e.mailboxRole = com.example.Email.Entity.EmailMailboxRole.RECIPIENT and m.status = com.example.Email.Entity.EmailMessageStatus.SENT and e.archived = false and e.spam = false and e.trashedAt is null)
            or (:folder = 'SENT' and e.mailboxRole = com.example.Email.Entity.EmailMailboxRole.SENDER and m.status = com.example.Email.Entity.EmailMessageStatus.SENT and e.trashedAt is null)
            or (:folder = 'DRAFTS' and e.mailboxRole = com.example.Email.Entity.EmailMailboxRole.SENDER and m.status = com.example.Email.Entity.EmailMessageStatus.DRAFT and e.trashedAt is null)
            or (:folder = 'STARRED' and e.starred = true and e.trashedAt is null)
            or (:folder = 'IMPORTANT' and e.important = true and e.trashedAt is null)
            or (:folder = 'ARCHIVE' and e.archived = true and e.trashedAt is null)
            or (:folder = 'SPAM' and e.spam = true and e.trashedAt is null)
            or (:folder = 'TRASH' and e.trashedAt is not null)
          )
          and (:accountId is null or m.account.accountId = :accountId)
          and (:unreadOnly = false or e.read = false)
          and (:search = '' or lower(m.subject) like lower(concat('%', :search, '%'))
               or lower(m.body) like lower(concat('%', :search, '%'))
               or lower(m.sender.fullName) like lower(concat('%', :search, '%'))
               or lower(m.sender.email) like lower(concat('%', :search, '%'))
               or lower(coalesce(m.externalSenderName, '')) like lower(concat('%', :search, '%'))
               or lower(coalesce(m.externalSenderEmail, '')) like lower(concat('%', :search, '%')))
        """)
    Page<EmailMailboxEntry> findMailbox(@Param("userId") Long userId, @Param("folder") String folder,
                                        @Param("accountId") Long accountId, @Param("search") String search,
                                        @Param("unreadOnly") boolean unreadOnly,
                                        Pageable pageable);

    @Query(value="select e from EmailMailboxEntry e join fetch e.message m join fetch m.sender where e.user.userId=:userId and (m.account is null or m.account.status<>com.example.Email.Entity.EmailAccountStatus.DISCONNECTED) and e.customFolder.folderId=:folderId and e.trashedAt is null and (:search='' or lower(m.subject) like lower(concat('%',:search,'%')) or lower(m.body) like lower(concat('%',:search,'%'))) order by coalesce(m.sentAt,m.createdAt) desc, m.messageId desc", countQuery="select count(e) from EmailMailboxEntry e join e.message m where e.user.userId=:userId and (m.account is null or m.account.status<>com.example.Email.Entity.EmailAccountStatus.DISCONNECTED) and e.customFolder.folderId=:folderId and e.trashedAt is null and (:search='' or lower(m.subject) like lower(concat('%',:search,'%')) or lower(m.body) like lower(concat('%',:search,'%')))")
    Page<EmailMailboxEntry> findCustomFolder(@Param("userId") Long userId,@Param("folderId") Long folderId,@Param("search") String search,Pageable pageable);

    @Query(value="select e from EmailMailboxEntry e join fetch e.message m join fetch m.sender where e.user.userId=:userId and (m.account is null or m.account.status<>com.example.Email.Entity.EmailAccountStatus.DISCONNECTED) and e.trashedAt is null and exists (select ml.messageLabelId from EmailMessageLabel ml where ml.message=m and ml.userId=:userId and ml.label.labelId=:labelId) and (:search='' or lower(m.subject) like lower(concat('%',:search,'%')) or lower(m.body) like lower(concat('%',:search,'%'))) order by coalesce(m.sentAt,m.createdAt) desc, m.messageId desc", countQuery="select count(e) from EmailMailboxEntry e join e.message m where e.user.userId=:userId and (m.account is null or m.account.status<>com.example.Email.Entity.EmailAccountStatus.DISCONNECTED) and e.trashedAt is null and exists (select ml.messageLabelId from EmailMessageLabel ml where ml.message=m and ml.userId=:userId and ml.label.labelId=:labelId) and (:search='' or lower(m.subject) like lower(concat('%',:search,'%')) or lower(m.body) like lower(concat('%',:search,'%')))")
    Page<EmailMailboxEntry> findLabel(@Param("userId") Long userId,@Param("labelId") Long labelId,@Param("search") String search,Pageable pageable);

    @Query("select e from EmailMailboxEntry e join fetch e.message m join fetch m.sender where e.id = :id")
    Optional<EmailMailboxEntry> findDetailed(@Param("id") EmailMailboxEntryId id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EmailMailboxEntry e join fetch e.message m join fetch m.sender where e.id = :id")
    Optional<EmailMailboxEntry> findByIdForUpdate(@Param("id") EmailMailboxEntryId id);

    @Query("""
      select new com.example.Email.DTO.Response.EmailFolderCountsResponse(
        sum(case when e.mailboxRole = com.example.Email.Entity.EmailMailboxRole.RECIPIENT and m.status = com.example.Email.Entity.EmailMessageStatus.SENT and e.archived = false and e.spam = false and e.trashedAt is null then 1 else 0 end),
        sum(case when e.mailboxRole = com.example.Email.Entity.EmailMailboxRole.RECIPIENT and m.status = com.example.Email.Entity.EmailMessageStatus.SENT and e.read = false and e.archived = false and e.spam = false and e.trashedAt is null then 1 else 0 end),
        sum(case when e.mailboxRole = com.example.Email.Entity.EmailMailboxRole.SENDER and m.status = com.example.Email.Entity.EmailMessageStatus.SENT and e.trashedAt is null then 1 else 0 end),
        sum(case when e.mailboxRole = com.example.Email.Entity.EmailMailboxRole.SENDER and m.status = com.example.Email.Entity.EmailMessageStatus.DRAFT and e.trashedAt is null then 1 else 0 end),
        sum(case when e.starred = true and e.trashedAt is null then 1 else 0 end),
        sum(case when e.important = true and e.trashedAt is null then 1 else 0 end),
        sum(case when e.archived = true and e.trashedAt is null then 1 else 0 end),
        sum(case when e.spam = true and e.trashedAt is null then 1 else 0 end),
        sum(case when e.trashedAt is not null then 1 else 0 end))
      from EmailMailboxEntry e join e.message m where e.user.userId = :userId
        and (m.account is null or m.account.status <> com.example.Email.Entity.EmailAccountStatus.DISCONNECTED)
      """)
    EmailFolderCountsResponse countFolders(@Param("userId") Long userId);
}
