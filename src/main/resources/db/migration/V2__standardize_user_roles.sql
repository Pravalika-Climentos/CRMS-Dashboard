-- Normalize the existing users.role column to the controlled CRM role set.
-- Existing sales managers become MANAGER. All unrecognized legacy values use
-- the least-privileged application role so no user gains elevated access.

UPDATE users
SET role = CASE
    WHEN UPPER(REPLACE(TRIM(role), ' ', '_')) = 'ADMIN' THEN 'ADMIN'
    WHEN UPPER(REPLACE(TRIM(role), ' ', '_')) IN ('MANAGER', 'SALES_MANAGER') THEN 'MANAGER'
    WHEN UPPER(REPLACE(TRIM(role), ' ', '_')) = 'SALES_EXECUTIVE' THEN 'SALES_EXECUTIVE'
    ELSE 'SALES_EXECUTIVE'
END;

ALTER TABLE users
    MODIFY COLUMN role VARCHAR(50) NOT NULL DEFAULT 'SALES_EXECUTIVE';

ALTER TABLE users
    ADD CONSTRAINT chk_users_role
        CHECK (role IN ('ADMIN', 'MANAGER', 'SALES_EXECUTIVE'));
