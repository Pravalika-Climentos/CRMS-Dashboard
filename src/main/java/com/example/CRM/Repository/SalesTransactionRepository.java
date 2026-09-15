package com.example.CRM.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.CRM.Entity.SalesTransaction;

import java.time.LocalDate;
import java.util.List;

public interface SalesTransactionRepository
        extends JpaRepository<SalesTransaction, Long> {

    List<SalesTransaction> findBySalesUser_UserId(Long userId);

    List<SalesTransaction> findByDeal_DealId(Long dealId);

    List<SalesTransaction> findByTransactionDateBetween(
            LocalDate startDate,
            LocalDate endDate
    );
}
