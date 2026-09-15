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
    name = "sales_transactions",
    indexes = {
        @Index(name = "idx_sales_deal", columnList = "deal_id"),
        @Index(name = "idx_sales_product", columnList = "product_id"),
        @Index(name = "idx_sales_user", columnList = "sales_user_id"),
        @Index(name = "idx_sales_date", columnList = "transaction_date"),
        @Index(name = "idx_sales_payment_status", columnList = "payment_status")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class SalesTransaction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transaction_id")
    private Long transactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "deal_id",
        foreignKey = @ForeignKey(name = "fk_sales_deal")
    )
    private Deal deal;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "product_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_sales_product")
    )
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "sales_user_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_sales_user")
    )
    private User salesUser;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(nullable = false)
    private Integer quantity;

    @Column(
        name = "unit_price",
        nullable = false,
        precision = 15,
        scale = 2
    )
    private BigDecimal unitPrice;

    @Column(
        name = "total_amount",
        insertable = false,
        updatable = false,
        precision = 15,
        scale = 2
    )
    private BigDecimal totalAmount;

    @Column(
        name = "cost_amount",
        nullable = false,
        precision = 15,
        scale = 2
    )
    private BigDecimal costAmount = BigDecimal.ZERO;

    @Column(name = "payment_status", nullable = false, length = 30)
    private String paymentStatus = "PAID";
}