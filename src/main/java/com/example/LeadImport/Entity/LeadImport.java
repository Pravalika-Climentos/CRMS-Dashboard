package com.example.LeadImport.Entity;

import com.example.CRM.Entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity @Table(name="lead_imports") @Getter @Setter @NoArgsConstructor
public class LeadImport {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="import_id") private Long importId;
 @Column(name="file_name",nullable=false,length=255) private String fileName;
 @Enumerated(EnumType.STRING) @Column(name="file_type",nullable=false,length=20) private LeadImportFileType fileType;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private LeadImportStatus status;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="uploaded_by_user_id",nullable=false) private User uploadedBy;
 @Column(name="detected_columns_json",nullable=false,columnDefinition="json") private String detectedColumnsJson;
 @Column(name="column_mapping_json",columnDefinition="json") private String columnMappingJson;
 @Column(name="total_records",nullable=false) private int totalRecords;
 @Column(name="valid_records",nullable=false) private int validRecords;
 @Column(name="invalid_records",nullable=false) private int invalidRecords;
 @Column(name="duplicate_records",nullable=false) private int duplicateRecords;
 @Column(name="imported_records",nullable=false) private int importedRecords;
 @Column(name="failure_message",length=1000) private String failureMessage;
 @Column(name="created_at",nullable=false,updatable=false) private LocalDateTime createdAt;
 @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt;
 @Column(name="completed_at") private LocalDateTime completedAt;
 @PrePersist void insert(){var now=LocalDateTime.now();createdAt=now;updatedAt=now;}
 @PreUpdate void update(){updatedAt=LocalDateTime.now();}
}
