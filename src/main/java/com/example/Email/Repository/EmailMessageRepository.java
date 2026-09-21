package com.example.Email.Repository;

import com.example.Email.Entity.EmailMessage;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface EmailMessageRepository extends JpaRepository<EmailMessage, Long> {
    Optional<EmailMessage> findByAccountAccountIdAndProviderMessageId(Long accountId, String providerMessageId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from EmailMessage m join fetch m.sender where m.messageId = :messageId")
    Optional<EmailMessage> findByIdForUpdate(@Param("messageId") Long messageId);
}
