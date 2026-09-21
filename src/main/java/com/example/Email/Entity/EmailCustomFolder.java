package com.example.Email.Entity;

import com.example.CRM.Entity.User;
import com.example.Common.Entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name="email_custom_folders", uniqueConstraints=@UniqueConstraint(name="uq_email_folders_user_name", columnNames={"user_id","name"}))
@Getter @Setter @NoArgsConstructor
public class EmailCustomFolder extends BaseEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="folder_id") private Long folderId;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="user_id", nullable=false) private User user;
    @Column(nullable=false, length=80) private String name;
    @Column(nullable=false, length=20) private String color;
    @Column(name="display_order", nullable=false) private Integer displayOrder = 0;
}
