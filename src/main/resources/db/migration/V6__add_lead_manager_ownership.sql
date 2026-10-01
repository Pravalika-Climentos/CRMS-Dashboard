ALTER TABLE leads
    ADD COLUMN manager_owner_id BIGINT UNSIGNED NULL AFTER assigned_user_id,
    ADD INDEX idx_leads_manager_owner (manager_owner_id),
    ADD CONSTRAINT fk_leads_manager_owner FOREIGN KEY (manager_owner_id)
        REFERENCES users(user_id) ON DELETE SET NULL;

ALTER TABLE lead_history
    ADD COLUMN old_manager_owner_id BIGINT UNSIGNED NULL AFTER new_assigned_user_id,
    ADD COLUMN new_manager_owner_id BIGINT UNSIGNED NULL AFTER old_manager_owner_id,
    ADD CONSTRAINT fk_lead_history_old_manager FOREIGN KEY (old_manager_owner_id)
        REFERENCES users(user_id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_lead_history_new_manager FOREIGN KEY (new_manager_owner_id)
        REFERENCES users(user_id) ON DELETE SET NULL,
    DROP CHECK chk_lead_history_type,
    ADD CONSTRAINT chk_lead_history_type CHECK (
        change_type IN ('CREATED','IMPORTED','ASSIGNED','REASSIGNED','MANAGER_ASSIGNED','MANAGER_REASSIGNED','STATUS_CHANGED','CONVERTED','UPDATED')
    );

UPDATE leads AS target_lead
JOIN users AS executive ON executive.user_id = target_lead.assigned_user_id
SET target_lead.manager_owner_id = executive.manager_user_id
WHERE target_lead.manager_owner_id IS NULL
  AND executive.manager_user_id IS NOT NULL;
