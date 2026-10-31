ALTER TABLE notification_settings
    ADD COLUMN task_alert_enabled BOOLEAN NOT NULL DEFAULT TRUE;