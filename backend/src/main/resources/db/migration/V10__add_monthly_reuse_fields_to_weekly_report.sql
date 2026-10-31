-- src/main/resources/db/migration/V8__add_monthly_reuse_fields_to_weekly_report.sql

ALTER TABLE weekly_report
    ADD COLUMN total_task_count INT NOT NULL DEFAULT 0,
    ADD COLUMN goal_weekly_breakdown_json TEXT NULL;