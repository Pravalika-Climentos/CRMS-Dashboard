package com.example.Email.Repository;

import com.example.Email.Entity.EmailAttachment;
import com.example.Email.Repository.Projection.EmailAttachmentCountProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EmailAttachmentRepository extends JpaRepository<EmailAttachment, Long> {
    List<EmailAttachment> findByMessageMessageIdOrderByAttachmentId(Long messageId);
    long countByMessageMessageId(Long messageId);
    Optional<EmailAttachment> findByAttachmentIdAndMessageMessageId(Long attachmentId, Long messageId);
    Optional<EmailAttachment> findByMessageMessageIdAndStoredFilename(Long messageId, String storedFilename);

    @Query("select a.message.messageId as messageId, count(a) as attachmentCount from EmailAttachment a where a.message.messageId in :messageIds group by a.message.messageId")
    List<EmailAttachmentCountProjection> countByMessageIds(@Param("messageIds") Collection<Long> messageIds);
}
