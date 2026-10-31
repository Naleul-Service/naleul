-- src/main/resources/db/migration/V5__add_premium_to_users.sql

ALTER TABLE users
    ADD COLUMN is_premium BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN premium_expires_at DATETIME NULL;