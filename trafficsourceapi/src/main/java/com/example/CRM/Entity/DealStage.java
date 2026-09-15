package com.example.CRM.Entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

import com.example.Common.Entity.BaseEntity;

@Entity
@Table(
    name = "deal_stages",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_deal_stages_name",
            columnNames = "stage_name"
        ),
        @UniqueConstraint(
            name = "uq_deal_stages_order",
            columnNames = "stage_order"
        )
    },
    indexes = {
        @Index(name = "idx_deal_stages_active", columnList = "active")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class DealStage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stage_id")
    private Long stageId;

    @Column(name = "stage_name", nullable = false, length = 80)
    private String stageName;

    @Column(name = "stage_order", nullable = false)
    private Integer stageOrder;

    @Column(
        nullable = false,
        precision = 5,
        scale = 2
    )
    private BigDecimal probability = BigDecimal.ZERO;

    @Column(nullable = false)
    private Boolean active = true;
}
