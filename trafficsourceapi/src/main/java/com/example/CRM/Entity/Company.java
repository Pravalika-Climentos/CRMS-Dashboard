package com.example.CRM.Entity;

import com.example.Common.Entity.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "companies",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_companies_name",
            columnNames = "company_name"
        )
    },
    indexes = {
        @Index(name = "idx_companies_industry", columnList = "industry"),
        @Index(name = "idx_companies_user", columnList = "assigned_user_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class Company extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "company_name", nullable = false, length = 150)
    private String companyName;

    @Column(length = 100)
    private String industry;

    @Column(length = 150)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(length = 255)
    private String website;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "assigned_user_id",
        foreignKey = @ForeignKey(name = "fk_companies_user")
    )
    private User assignedUser;
}
