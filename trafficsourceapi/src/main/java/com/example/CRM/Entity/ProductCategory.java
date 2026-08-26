package com.example.CRM.Entity;

import com.example.Common.Entity.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "product_categories",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_product_categories_name",
            columnNames = "category_name"
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
public class ProductCategory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "category_name", nullable = false, length = 100)
    private String categoryName;

    @Column(length = 255)
    private String description;
}