package com.example.Email.Entity;
import com.example.CRM.Entity.User;import com.example.Common.Entity.BaseEntity;import jakarta.persistence.*;import lombok.*;import java.time.Instant;
@Entity @Table(name="email_accounts",uniqueConstraints=@UniqueConstraint(name="uq_email_accounts_user_provider_address",columnNames={"user_id","provider","email_address"})) @Getter @Setter @NoArgsConstructor
public class EmailAccount extends BaseEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="account_id") private Long accountId;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="user_id",nullable=false) private User user;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private EmailProvider provider;
 @Column(name="email_address",nullable=false,length=254) private String emailAddress; @Column(name="display_name",length=150) private String displayName;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private EmailAccountStatus status=EmailAccountStatus.PENDING;
 @Column(name="encrypted_access_token",columnDefinition="LONGTEXT") private String encryptedAccessToken; @Column(name="encrypted_refresh_token",columnDefinition="LONGTEXT") private String encryptedRefreshToken;
 @Column(name="token_expires_at") private Instant tokenExpiresAt; @Column(name="granted_scopes",columnDefinition="TEXT") private String grantedScopes;
 @Column(name="sync_cursor",columnDefinition="TEXT") private String syncCursor; @Column(name="last_sync_at") private Instant lastSyncAt; @Column(name="last_sync_error",length=1000) private String lastSyncError;
 @Column(name="subscription_id",length=255) private String subscriptionId; @Column(name="subscription_expires_at") private Instant subscriptionExpiresAt;
 @Version @Column(nullable=false) private Long version;
}
