-- V18__add_actual_time_to_task.sql
ALTER TABLE task ADD COLUMN actual_start_at DATETIME(6) NULL;
ALTER TABLE task ADD COLUMN actual_end_at DATETIME(6) NULL;