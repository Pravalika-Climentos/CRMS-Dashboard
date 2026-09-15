package com.example.CRM.Repository;

import com.example.CRM.Entity.User;
import com.example.CRM.Repository.Projection.ChatUserProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmail(String email);

    @Query("""
        SELECT u.userId AS userId, u.fullName AS fullName, u.email AS email,
               u.designation AS designation, u.role AS role, u.avatar AS avatar
        FROM User u
        WHERE u.active = true AND u.userId <> :currentUserId
          AND (LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))
        ORDER BY u.fullName
        """)
    List<ChatUserProjection> searchChatUsers(@Param("search") String search,
                                             @Param("currentUserId") Long currentUserId,
                                             Pageable pageable);

    @Query("SELECT COUNT(u) FROM User u WHERE u.userId IN :userIds AND u.active = true")
    long countActiveUsersByIds(@Param("userIds") Collection<Long> userIds);

    List<User> findAllByUserIdInAndActiveTrue(Collection<Long> userIds);

    @Query(value = """
        SELECT user FROM User user
        WHERE user.active = true AND user.userId <> :currentUserId
          AND (:search = '' OR LOWER(user.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(user.email) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(COALESCE(user.designation, '')) LIKE LOWER(CONCAT('%', :search, '%')))
        ORDER BY user.fullName ASC
        """,
        countQuery = """
        SELECT COUNT(user) FROM User user
        WHERE user.active = true AND user.userId <> :currentUserId
          AND (:search = '' OR LOWER(user.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(user.email) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(COALESCE(user.designation, '')) LIKE LOWER(CONCAT('%', :search, '%')))
        """)
    Page<User> searchActiveCalendarUsers(@Param("search") String search,
                                         @Param("currentUserId") Long currentUserId,
                                         Pageable pageable);
}
