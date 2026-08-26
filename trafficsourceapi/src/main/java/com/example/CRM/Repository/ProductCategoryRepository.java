package com.example.CRM.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.CRM.Entity.ProductCategory;

import java.util.Optional;

public interface ProductCategoryRepository
        extends JpaRepository<ProductCategory, Long> {

    Optional<ProductCategory> findByCategoryName(String categoryName);

    boolean existsByCategoryName(String categoryName);
}
