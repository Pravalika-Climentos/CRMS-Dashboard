CREATE TABLE lead_imports (
    import_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    uploaded_by_user_id BIGINT UNSIGNED NOT NULL,
    detected_columns_json JSON NOT NULL,
    column_mapping_json JSON NULL,
    total_records INT NOT NULL DEFAULT 0,
    valid_records INT NOT NULL DEFAULT 0,
    invalid_records INT NOT NULL DEFAULT 0,
    duplicate_records INT NOT NULL DEFAULT 0,
    imported_records INT NOT NULL DEFAULT 0,
    failure_message VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (import_id),
    INDEX idx_lead_imports_owner_created (uploaded_by_user_id, created_at),
    INDEX idx_lead_imports_status (status),
    CONSTRAINT fk_lead_imports_uploader FOREIGN KEY (uploaded_by_user_id)
        REFERENCES users(user_id) ON DELETE RESTRICT,
    CONSTRAINT chk_lead_imports_type CHECK (file_type IN ('CSV','XLS','XLSX','JSON')),
    CONSTRAINT chk_lead_imports_status CHECK (status IN ('UPLOADED','MAPPED','VALIDATED','COMPLETED','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE lead_import_rows (
    row_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    import_id BIGINT UNSIGNED NOT NULL,
    source_row_number INT NOT NULL,
    raw_data_json JSON NOT NULL,
    normalized_data_json JSON NULL,
    validation_errors_json JSON NULL,
    valid_row BOOLEAN NOT NULL DEFAULT FALSE,
    duplicate_row BOOLEAN NOT NULL DEFAULT FALSE,
    imported_lead_id BIGINT UNSIGNED NULL,
    PRIMARY KEY (row_id),
    UNIQUE KEY uq_lead_import_row_number (import_id, source_row_number),
    INDEX idx_lead_import_rows_preview (import_id, valid_row, duplicate_row, source_row_number),
    CONSTRAINT fk_lead_import_rows_import FOREIGN KEY (import_id)
        REFERENCES lead_imports(import_id) ON DELETE CASCADE,
    CONSTRAINT fk_lead_import_rows_lead FOREIGN KEY (imported_lead_id)
        REFERENCES leads(lead_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE leads
    ADD COLUMN geo_location VARCHAR(200) NULL AFTER phone,
    ADD COLUMN state VARCHAR(100) NULL AFTER geo_location,
    ADD COLUMN interested_service VARCHAR(150) NULL AFTER state,
    ADD COLUMN personal_details JSON NULL AFTER interested_service,
    ADD COLUMN external_id VARCHAR(150) NULL AFTER personal_details,
    ADD COLUMN duplicate_hash CHAR(64) NULL AFTER external_id,
    ADD INDEX idx_leads_duplicate_hash (duplicate_hash),
    ADD INDEX idx_leads_email_phone (email, phone);
