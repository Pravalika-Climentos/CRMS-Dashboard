package com.example.CRM.Entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(
    name = "teams",
    indexes = {
        @Index(
            name = "idx_teams_owner",
            columnList = "owner_id, active"
        ),
        @Index(
            name = "idx_teams_name",
            columnList = "name"
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "team_id")
    private Long teamId;

    @Column(
        name = "name",
        nullable = false,
        length = 150
    )
    private String name;

    @Column(
        name = "description",
        length = 1000
    )
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "owner_id",
        nullable = false,
        foreignKey = @ForeignKey(
            name = "fk_teams_owner"
        )
    )
    private User owner;

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