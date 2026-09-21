package com.example.Email.Repository; import com.example.Email.Entity.EmailOAuthState;import org.springframework.data.jpa.repository.JpaRepository;
public interface EmailOAuthStateRepository extends JpaRepository<EmailOAuthState,String>{}
