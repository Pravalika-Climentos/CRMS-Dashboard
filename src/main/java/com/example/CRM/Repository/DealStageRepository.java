package com.example.CRM.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.CRM.Entity.DealStage;

import java.util.List;
import java.util.Optional;

public interface DealStageRepository extends JpaRepository<DealStage, Long> {

    Optional<DealStage> findByStageName(String stageName);

    List<DealStage> findByActiveTrueOrderByStageOrderAsc();
}
