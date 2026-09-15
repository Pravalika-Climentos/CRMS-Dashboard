package com.example.CRM.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.CRM.Entity.Company;

import java.util.List;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    boolean existsByCompanyName(String companyName);

    List<Company> findByAssignedUser_UserId(Long userId);

    List<Company> findByIndustry(String industry);
}
