package com.example.CRM.Entity;

import com.example.Common.Entity.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "lead_sources",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_lead_sources_name",
            columnNames = "source_name"
        )
    },
    indexes = {
        @Index(name = "idx_lead_sources_active", columnList = "active")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class LeadSource extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "source_name", nullable = false, length = 100)
    private String sourceName;

    @Column(length = 255)
    private String description;

    @Column(nullable = false)
    private Boolean active = true;
}