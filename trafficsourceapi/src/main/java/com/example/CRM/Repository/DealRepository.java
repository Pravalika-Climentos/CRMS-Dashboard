package com.example.CRM.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.CRM.Entity.Deal;

import java.util.List;

public interface DealRepository extends JpaRepository<Deal, Long> {

    List<Deal> findByOwner_UserId(Long userId);

    List<Deal> findByCompany_CompanyId(Long companyId);

    List<Deal> findByStage_StageId(Long stageId);

    List<Deal> findByStatus(String status);
}
