package com.example.CRM.Entity;

import com.example.Common.Entity.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "users",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_users_email",
            columnNames = "email"
        )
    },
    indexes = {
        @Index(name = "idx_users_role", columnList = "role"),
        @Index(name = "idx_users_active", columnList = "active")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(length = 100)
    private String designation;

    @Column(nullable = false, length = 50)
    private String role = "SALES_EXECUTIVE";

    @Column(length = 255)
    private String avatar;

    @Column(nullable = false)
    private Boolean active = true;
}
