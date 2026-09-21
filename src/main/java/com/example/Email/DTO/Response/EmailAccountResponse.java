package com.example.Email.DTO.Response; import com.example.Email.Entity.*;import java.time.Instant;
public record EmailAccountResponse(Long accountId,EmailProvider provider,String emailAddress,String displayName,EmailAccountStatus status,Instant lastSyncAt,String lastSyncError,Instant subscriptionExpiresAt){}
