package com.example.Dashboard_Data.Repository;

import com.example.Dashboard_Data.Entity.SalesTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SalesTargetRepository extends JpaRepository<SalesTarget, Long> {
    Optional<SalesTarget> findByTargetMonth(LocalDate targetMonth);
    Optional<SalesTarget> findFirstByTargetMonthLessThanEqualOrderByTargetMonthDesc(LocalDate targetMonth);
    List<SalesTarget> findAllByOrderByTargetMonthDesc();
}
