-- Baseline source: Dump20260921.sql
-- Consolidated schema changes introduced after that dump.
--
-- IF NOT EXISTS allows this migration to adopt an environment where the
-- sales_targets table was created manually before Flyway was enabled.

CREATE TABLE IF NOT EXISTS sales_targets (
    target_id BIGINT NOT NULL AUTO_INCREMENT,
    target_month DATE NOT NULL,
    target_amount DECIMAL(15,2) NOT NULL,
    currency_code CHAR(3) NOT NULL DEFAULT 'INR',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    PRIMARY KEY (target_id),
    CONSTRAINT uq_sales_targets_month UNIQUE (target_month),
    CONSTRAINT chk_sales_targets_amount CHECK (target_amount >= 0),
    INDEX idx_sales_targets_month (target_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
