-- V{다음번호}__add_notification_enabled_to_routine.sql
ALTER TABLE routine
    ADD COLUMN notification_enabled BOOLEAN NOT NULL DEFAULT TRUE;