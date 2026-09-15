package com.example.CRM.Entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.example.Common.Entity.BaseEntity;

@Entity
@Table(
    name = "deals",
    indexes = {
        @Index(name = "idx_deals_company", columnList = "company_id"),
        @Index(name = "idx_deals_contact", columnList = "contact_id"),
        @Index(name = "idx_deals_stage", columnList = "stage_id"),
        @Index(name = "idx_deals_owner", columnList = "owner_user_id"),
        @Index(name = "idx_deals_status", columnList = "status"),
        @Index(name = "idx_deals_close_date", columnList = "expected_close_date"),
        @Index(name = "idx_deals_created_at", columnList = "created_at"),
        @Index(name = "idx_deals_won_date", columnList = "won_date")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class Deal extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "deal_id")
    private Long dealId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "lead_id",
        foreignKey = @ForeignKey(name = "fk_deals_lead")
    )
    private Lead lead;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "company_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_deals_company")
    )
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "contact_id",
        foreignKey = @ForeignKey(name = "fk_deals_contact")
    )
    private Contact contact;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "stage_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_deals_stage")
    )
    private DealStage stage;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "owner_user_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_deals_owner")
    )
    private User owner;

    @Column(name = "deal_name", nullable = false, length = 180)
    private String dealName;

    @Column(
        name = "deal_value",
        nullable = false,
        precision = 15,
        scale = 2
    )
    private BigDecimal dealValue = BigDecimal.ZERO;

    @Column(
        nullable = false,
        precision = 5,
        scale = 2
    )
    private BigDecimal probability = BigDecimal.ZERO;

    @Column(name = "expected_close_date")
    private LocalDate expectedCloseDate;

    @Column(nullable = false, length = 30)
    private String status = "OPEN";

    @Column(name = "won_date")
    private LocalDate wonDate;

    @Column(name = "lost_reason", length = 255)
    private String lostReason;
}
