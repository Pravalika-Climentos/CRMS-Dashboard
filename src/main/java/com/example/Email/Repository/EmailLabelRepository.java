package com.example.Email.Repository;
import com.example.Email.Entity.EmailLabel;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface EmailLabelRepository extends JpaRepository<EmailLabel,Long> {
 List<EmailLabel> findByUserUserIdOrderByNameAsc(Long userId);
 Optional<EmailLabel> findByLabelIdAndUserUserId(Long id, Long userId);
 boolean existsByUserUserIdAndNameIgnoreCase(Long userId,String name);
}
