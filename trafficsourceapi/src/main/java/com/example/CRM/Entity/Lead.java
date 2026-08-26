package com.example.CRM.Entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

import com.example.Common.Entity.BaseEntity;

@Entity
@Table(
    name = "leads",
    indexes = {
        @Index(name = "idx_leads_company", columnList = "company_id"),
        @Index(name = "idx_leads_contact", columnList = "contact_id"),
        @Index(name = "idx_leads_source", columnList = "source_id"),
        @Index(name = "idx_leads_user", columnList = "assigned_user_id"),
        @Index(name = "idx_leads_status", columnList = "status"),
        @Index(name = "idx_leads_created_at", columnList = "created_at"),
        @Index(name = "idx_leads_converted", columnList = "converted")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class Lead extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "lead_id")
    private Long leadId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "company_id",
        foreignKey = @ForeignKey(name = "fk_leads_company")
    )
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "contact_id",
        foreignKey = @ForeignKey(name = "fk_leads_contact")
    )
    private Contact contact;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "source_id",
        foreignKey = @ForeignKey(name = "fk_leads_source")
    )
    private LeadSource source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "assigned_user_id",
        foreignKey = @ForeignKey(name = "fk_leads_user")
    )
    private User assignedUser;

    @Column(name = "lead_name", nullable = false, length = 150)
    private String leadName;

    @Column(length = 150)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(nullable = false, length = 40)
    private String status = "NEW";

    @Column(
        name = "estimated_value",
        nullable = false,
        precision = 15,
        scale = 2
    )
    private BigDecimal estimatedValue = BigDecimal.ZERO;

    @Column(nullable = false)
    private Boolean converted = false;
}
