package com.example.Email.Repository;

import com.example.Email.Entity.EmailRecipient;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface EmailRecipientRepository extends JpaRepository<EmailRecipient, Long> {
    @Query("select r from EmailRecipient r left join fetch r.recipientUser where r.message.messageId = :messageId order by r.recipientId")
    List<EmailRecipient> findAllByMessageId(@Param("messageId") Long messageId);
    void deleteByMessageMessageId(Long messageId);
}
