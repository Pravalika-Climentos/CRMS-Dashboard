package com.example.Email.DTO.Response;
import com.example.Email.Entity.*;
public record EmailRecipientResponse(Long recipientId, String emailAddress, EmailRecipientType type,
                                     EmailDeliveryStatus deliveryStatus, EmailUserResponse user) {}
