ALTER TABLE leads
    ADD COLUMN public_reference VARCHAR(24) NULL AFTER lead_id;

UPDATE leads
SET public_reference = CONCAT(
    'LD-',
    UPPER(SUBSTRING(REPLACE(UUID(), '-', ''), 1, 12))
)
WHERE public_reference IS NULL;

ALTER TABLE leads
    MODIFY COLUMN public_reference VARCHAR(24) NOT NULL,
    ADD CONSTRAINT uq_leads_public_reference UNIQUE (public_reference);

CREATE TABLE lead_tracking_portals (
    tracking_portal_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    portal_key VARCHAR(50) NOT NULL,
    organization_name VARCHAR(150) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    expires_at DATETIME(6) NULL,
    last_accessed_at DATETIME(6) NULL,
    revoked_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (tracking_portal_id),
    CONSTRAINT uq_lead_tracking_portals_key UNIQUE (portal_key),
    CONSTRAINT uq_lead_tracking_portals_token UNIQUE (token_hash),
    INDEX idx_lead_tracking_portals_access (active, expires_at)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
