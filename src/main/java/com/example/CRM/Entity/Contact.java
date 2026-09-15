package com.example.CRM.Entity;

import com.example.Common.Entity.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "contacts",
    indexes = {
        @Index(name = "idx_contacts_company", columnList = "company_id"),
        @Index(name = "idx_contacts_email", columnList = "email"),
        @Index(name = "idx_contacts_status", columnList = "status")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class Contact extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "contact_id")
    private Long contactId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "company_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_contacts_company")
    )
    private Company company;

    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    @Column(length = 150)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(length = 100)
    private String designation;

    @Column(nullable = false, length = 30)
    private String status = "ACTIVE";
}