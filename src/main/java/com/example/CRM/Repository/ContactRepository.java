package com.example.CRM.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.CRM.Entity.Contact;

import java.util.List;
import java.util.Optional;

public interface ContactRepository extends JpaRepository<Contact, Long> {

    List<Contact> findByCompany_CompanyId(Long companyId);

    List<Contact> findByStatus(String status);
    Optional<Contact> findFirstByEmailIgnoreCase(String email);
}
