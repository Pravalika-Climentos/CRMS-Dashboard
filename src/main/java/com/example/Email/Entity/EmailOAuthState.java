package com.example.Email.Entity;
import jakarta.persistence.*;import lombok.*;import java.time.Instant;
@Entity @Table(name="email_oauth_states") @Getter @Setter @NoArgsConstructor
public class EmailOAuthState { @Id @Column(name="state_hash",length=64) private String stateHash; @Column(name="user_id",nullable=false) private Long userId; @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private EmailProvider provider; @Column(name="redirect_uri",nullable=false,length=500) private String redirectUri; @Column(name="expires_at",nullable=false) private Instant expiresAt; @Column(name="created_at",nullable=false) private Instant createdAt=Instant.now(); }
