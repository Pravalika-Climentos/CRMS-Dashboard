CREATE TABLE email_accounts (
 account_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,user_id BIGINT UNSIGNED NOT NULL,
 provider VARCHAR(30) NOT NULL,email_address VARCHAR(254) NOT NULL,display_name VARCHAR(150) NULL,status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
 encrypted_access_token LONGTEXT NULL,encrypted_refresh_token LONGTEXT NULL,token_expires_at DATETIME(6) NULL,granted_scopes TEXT NULL,
 sync_cursor TEXT NULL,last_sync_at DATETIME(6) NULL,last_sync_error VARCHAR(1000) NULL,subscription_id VARCHAR(255) NULL,subscription_expires_at DATETIME(6) NULL,
 version BIGINT NOT NULL DEFAULT 0,created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),created_by BIGINT UNSIGNED NULL,updated_by BIGINT UNSIGNED NULL,
 PRIMARY KEY(account_id),UNIQUE KEY uq_email_accounts_user_provider_address(user_id,provider,email_address),KEY idx_email_accounts_sync(status,last_sync_at),
 CONSTRAINT fk_email_accounts_user FOREIGN KEY(user_id) REFERENCES users(user_id) ON DELETE CASCADE,
 CONSTRAINT ck_email_accounts_provider CHECK(provider IN('INTERNAL','GMAIL','MICROSOFT_365','IMAP_SMTP')),
 CONSTRAINT ck_email_accounts_status CHECK(status IN('PENDING','CONNECTED','SYNCING','REAUTHORIZATION_REQUIRED','ERROR','DISCONNECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE email_messages ADD COLUMN account_id BIGINT UNSIGNED NULL,ADD COLUMN provider_message_id VARCHAR(255) NULL,ADD COLUMN provider_thread_id VARCHAR(255) NULL,ADD COLUMN internet_message_id VARCHAR(998) NULL,ADD COLUMN received_at DATETIME(6) NULL,ADD COLUMN html_body LONGTEXT NULL,ADD KEY idx_email_messages_account_provider(account_id,provider_message_id),ADD KEY idx_email_messages_internet_id(internet_message_id(191)),ADD CONSTRAINT fk_email_messages_account FOREIGN KEY(account_id) REFERENCES email_accounts(account_id) ON DELETE SET NULL;

CREATE TABLE email_crm_links (
 link_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,message_id BIGINT UNSIGNED NOT NULL,entity_type VARCHAR(30) NOT NULL,entity_id VARCHAR(64) NOT NULL,relationship_type VARCHAR(30) NOT NULL DEFAULT 'RELATED',linked_by_user_id BIGINT UNSIGNED NOT NULL,created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(link_id),UNIQUE KEY uq_email_crm_link(message_id,entity_type,entity_id,relationship_type),KEY idx_email_crm_entity(entity_type,entity_id,created_at),
 CONSTRAINT fk_email_crm_link_message FOREIGN KEY(message_id) REFERENCES email_messages(message_id) ON DELETE CASCADE,CONSTRAINT fk_email_crm_link_user FOREIGN KEY(linked_by_user_id) REFERENCES users(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE email_sync_jobs (
 job_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,account_id BIGINT UNSIGNED NOT NULL,job_type VARCHAR(30) NOT NULL,status VARCHAR(20) NOT NULL DEFAULT 'PENDING',attempt_count INT NOT NULL DEFAULT 0,available_at DATETIME(6) NOT NULL,started_at DATETIME(6) NULL,completed_at DATETIME(6) NULL,error_message VARCHAR(1000) NULL,created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 PRIMARY KEY(job_id),KEY idx_email_sync_jobs_claim(status,available_at),CONSTRAINT fk_email_sync_jobs_account FOREIGN KEY(account_id) REFERENCES email_accounts(account_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE email_oauth_states (state_hash CHAR(64) NOT NULL,user_id BIGINT UNSIGNED NOT NULL,provider VARCHAR(30) NOT NULL,redirect_uri VARCHAR(500) NOT NULL,expires_at DATETIME(6) NOT NULL,created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),PRIMARY KEY(state_hash),KEY idx_email_oauth_expiry(expires_at),CONSTRAINT fk_email_oauth_user FOREIGN KEY(user_id) REFERENCES users(user_id) ON DELETE CASCADE) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
