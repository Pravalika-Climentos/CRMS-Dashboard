package com.example.CRM.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.CRM.Entity.LeadSource;

import java.util.Optional;

public interface LeadSourceRepository extends JpaRepository<LeadSource, Long> {

    Optional<LeadSource> findBySourceName(String sourceName);

    boolean existsBySourceName(String sourceName);
}