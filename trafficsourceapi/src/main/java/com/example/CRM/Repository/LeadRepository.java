package com.example.CRM.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.CRM.Entity.Lead;

import java.util.List;

public interface LeadRepository extends JpaRepository<Lead, Long> {

    List<Lead> findByAssignedUser_UserId(Long userId);

    List<Lead> findBySource_SourceId(Long sourceId);

    List<Lead> findByStatus(String status);

    List<Lead> findByConverted(boolean converted);
}
