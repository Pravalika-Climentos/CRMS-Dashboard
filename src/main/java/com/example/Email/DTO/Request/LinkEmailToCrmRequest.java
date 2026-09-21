package com.example.Email.DTO.Request;import jakarta.validation.constraints.*;
public record LinkEmailToCrmRequest(@NotBlank @Pattern(regexp="CONTACT|LEAD|COMPANY|DEAL|CALENDAR_EVENT|CALL|TASK") String entityType,@NotBlank @Size(max=64) String entityId,@Pattern(regexp="RELATED|RECIPIENT|FOLLOW_UP|NOTIFICATION") String relationshipType){}
