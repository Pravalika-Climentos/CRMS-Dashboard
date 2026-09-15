package com.example.Calendar.Entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(
    name = "calendar_categories",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_calendar_category_owner_name",
            columnNames = {
                "owner_id",
                "name"
            }
        )
    },
    indexes = {
        @Index(
            name = "idx_calendar_categories_owner_active",
            columnList = "owner_id, active"
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
public class CalendarCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Long categoryId;

    @Column(
        name = "name",
        nullable = false,
        length = 100
    )
    private String name;

    @Column(
        name = "color",
        nullable = false,
        length = 7
    )
    private String color;

    @Column(
        name = "active",
        nullable = false
    )
    private Boolean active = true;

    @Version
    @Column(
        name = "version",
        nullable = false
    )
    private Long version = 0L;

    @CreationTimestamp
    @Column(
        name = "created_at",
        nullable = false,
        updatable = false
    )
    private Instant createdAt;

    @UpdateTimestamp
    @Column(
        name = "updated_at",
        nullable = false
    )
    private Instant updatedAt;
}