package com.example.CRM.Entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(
    name = "team_members",
    indexes = {
        @Index(
            name = "idx_team_members_user",
            columnList = "user_id, team_id"
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
public class TeamMember {

    @EmbeddedId
    private TeamMemberId id;

    @MapsId("teamId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "team_id",
        nullable = false,
        foreignKey = @ForeignKey(
            name = "fk_team_members_team"
        )
    )
    private Team team;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "user_id",
        nullable = false,
        foreignKey = @ForeignKey(
            name = "fk_team_members_user"
        )
    )
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "added_by",
        nullable = false,
        foreignKey = @ForeignKey(
            name = "fk_team_members_added_by"
        )
    )
    private User addedBy;

    @CreationTimestamp
    @Column(
        name = "joined_at",
        nullable = false,
        updatable = false
    )
    private Instant joinedAt;
}