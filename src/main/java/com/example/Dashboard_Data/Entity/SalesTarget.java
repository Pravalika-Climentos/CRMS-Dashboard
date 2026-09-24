package com.example.Dashboard_Data.Entity;

import com.example.Common.Entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "sales_targets",
        uniqueConstraints = @UniqueConstraint(name = "uq_sales_targets_month", columnNames = "target_month"),
        indexes = @Index(name = "idx_sales_targets_month", columnList = "target_month"))
@Getter @Setter @NoArgsConstructor
public class SalesTarget extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "target_month", nullable = false)
    private LocalDate targetMonth;

    @Column(name = "target_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal targetAmount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "INR";
}
