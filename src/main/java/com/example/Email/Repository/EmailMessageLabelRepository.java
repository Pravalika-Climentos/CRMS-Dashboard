package com.example.Email.Repository;
import com.example.Email.Entity.EmailMessageLabel;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface EmailMessageLabelRepository extends JpaRepository<EmailMessageLabel,Long> {
 boolean existsByMessageMessageIdAndUserIdAndLabelLabelId(Long messageId,Long userId,Long labelId);
 void deleteByMessageMessageIdAndUserIdAndLabelLabelId(Long messageId,Long userId,Long labelId);
 long countByLabelLabelId(Long labelId);
 @Modifying @Query("delete from EmailMessageLabel x where x.label.labelId=:id") void deleteAllForLabel(@Param("id") Long id);
}
