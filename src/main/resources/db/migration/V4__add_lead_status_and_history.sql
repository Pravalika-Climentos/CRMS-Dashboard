-- Normalize legacy status values before adding the constraint.

UPDATE leads
SET status = UPPER(TRIM(status));

UPDATE leads
SET status = 'CONVERTED'
WHERE converted = 1;

UPDATE leads
SET status = 'NEW'
WHERE status NOT IN (
    'NEW',
    'ASSIGNED',
    'CONTACTED',
    'QUALIFIED',
    'PROPOSAL',
    'NEGOTIATION',
    'CONVERTED',
    'LOST'
);

ALTER TABLE leads
    ADD COLUMN assigned_at DATETIME(6) NULL
        AFTER assigned_user_id,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0
        AFTER converted,
    ADD CONSTRAINT chk_leads_status
        CHECK (
            status IN (
                'NEW',
                'ASSIGNED',
                'CONTACTED',
                'QUALIFIED',
                'PROPOSAL',
                'NEGOTIATION',
                'CONVERTED',
                'LOST'
            )
        );

UPDATE leads
SET assigned_at = COALESCE(updated_at, created_at)
WHERE assigned_user_id IS NOT NULL
  AND assigned_at IS NULL;

CREATE TABLE lead_history (
    history_id BIGINT NOT NULL AUTO_INCREMENT,
    lead_id BIGINT UNSIGNED NOT NULL,
    change_type VARCHAR(30) NOT NULL,

    old_status VARCHAR(40) NULL,
    new_status VARCHAR(40) NULL,

    old_assigned_user_id BIGINT UNSIGNED NULL,
    new_assigned_user_id BIGINT UNSIGNED NULL,

    assignment_strategy VARCHAR(30) NULL,
    changed_by_user_id BIGINT UNSIGNED NULL,

    note VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (history_id),

    INDEX idx_lead_history_lead_created (
        lead_id,
        created_at
    ),

    INDEX idx_lead_history_changed_by (
        changed_by_user_id,
        created_at
    ),

    CONSTRAINT fk_lead_history_lead
        FOREIGN KEY (lead_id)
        REFERENCES leads(lead_id)
        ON DELETE CASCADE,

    CONSTRAINT fk_lead_history_old_assignee
        FOREIGN KEY (old_assigned_user_id)
        REFERENCES users(user_id)
        ON DELETE SET NULL,

    CONSTRAINT fk_lead_history_new_assignee
        FOREIGN KEY (new_assigned_user_id)
        REFERENCES users(user_id)
        ON DELETE SET NULL,

    CONSTRAINT fk_lead_history_changed_by
        FOREIGN KEY (changed_by_user_id)
        REFERENCES users(user_id)
        ON DELETE SET NULL,

    CONSTRAINT chk_lead_history_type
        CHECK (
            change_type IN (
                'CREATED',
                'IMPORTED',
                'ASSIGNED',
                'REASSIGNED',
                'STATUS_CHANGED',
                'CONVERTED',
                'UPDATED'
            )
        ),

    CONSTRAINT chk_lead_history_strategy
        CHECK (
            assignment_strategy IS NULL
            OR assignment_strategy IN (
                'MANUAL',
                'EQUAL',
                'ROUND_ROBIN',
                'IMPORT'
            )
        )
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
