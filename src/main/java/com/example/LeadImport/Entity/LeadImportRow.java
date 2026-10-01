package com.example.LeadImport.Entity;

import com.example.CRM.Entity.Lead;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name="lead_import_rows") @Getter @Setter @NoArgsConstructor
public class LeadImportRow {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="row_id") private Long rowId;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="import_id",nullable=false) private LeadImport leadImport;
 @Column(name="source_row_number",nullable=false) private int rowNumber;
 @Column(name="raw_data_json",nullable=false,columnDefinition="json") private String rawDataJson;
 @Column(name="normalized_data_json",columnDefinition="json") private String normalizedDataJson;
 @Column(name="validation_errors_json",columnDefinition="json") private String validationErrorsJson;
 @Column(name="valid_row",nullable=false) private boolean validRow;
 @Column(name="duplicate_row",nullable=false) private boolean duplicateRow;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="imported_lead_id") private Lead importedLead;
}
