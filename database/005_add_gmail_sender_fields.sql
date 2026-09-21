ALTER TABLE email_messages
    ADD COLUMN external_sender_name VARCHAR(255) NULL AFTER html_body,
    ADD COLUMN external_sender_email VARCHAR(254) NULL AFTER external_sender_name,
    ADD UNIQUE KEY uq_email_messages_account_provider (account_id, provider_message_id);
