ALTER TABLE users
    ADD COLUMN manager_user_id BIGINT UNSIGNED NULL AFTER role,
    ADD INDEX idx_users_manager (manager_user_id),
    ADD CONSTRAINT fk_users_manager
        FOREIGN KEY (manager_user_id)
        REFERENCES users(user_id)
        ON DELETE SET NULL;
