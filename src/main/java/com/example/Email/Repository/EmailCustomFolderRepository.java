package com.example.Email.Repository;
import com.example.Email.Entity.EmailCustomFolder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface EmailCustomFolderRepository extends JpaRepository<EmailCustomFolder,Long> {
 List<EmailCustomFolder> findByUserUserIdOrderByDisplayOrderAscNameAsc(Long userId);
 Optional<EmailCustomFolder> findByFolderIdAndUserUserId(Long id,Long userId);
 boolean existsByUserUserIdAndNameIgnoreCase(Long userId,String name);
}
