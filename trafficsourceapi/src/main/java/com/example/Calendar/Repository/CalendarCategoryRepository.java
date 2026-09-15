package com.example.Calendar.Repository;

import com.example.Calendar.Entity.CalendarCategory;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CalendarCategoryRepository
        extends JpaRepository<CalendarCategory, Long> {

    Page<CalendarCategory>
    findByActiveTrueAndNameContainingIgnoreCase(
            String search,
            Pageable pageable
    );

    Page<CalendarCategory>
    findByNameContainingIgnoreCase(
            String search,
            Pageable pageable
    );

    boolean existsByNameIgnoreCase(
            String name
    );

    boolean existsByNameIgnoreCaseAndCategoryIdNot(
            String name,
            Long categoryId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT category
            FROM CalendarCategory category
            WHERE category.categoryId = :categoryId
            """)
    Optional<CalendarCategory> findByIdForUpdate(
            @Param("categoryId") Long categoryId
    );
}