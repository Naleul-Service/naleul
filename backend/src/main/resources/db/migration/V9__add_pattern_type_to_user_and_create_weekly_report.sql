-- src/main/resources/db/migration/V9_add_pattern_type_to_user_and_create_weekly_report.sql

-- 1) users 테이블에 AI 패턴 유형 컬럼 추가
ALTER TABLE users
    ADD COLUMN pattern_type VARCHAR(30) NULL;

-- 2) 주간 리포트 저장 테이블 생성
CREATE TABLE weekly_report (
                               weekly_report_id            BIGINT AUTO_INCREMENT PRIMARY KEY,
                               user_id                     BIGINT NOT NULL,
                               week_start_date              DATE NOT NULL,
                               week_end_date                 DATE NOT NULL,
                               pattern_type_snapshot          VARCHAR(30) NOT NULL,

                               ai_feedback_title             TEXT,
                               ai_feedback_content            TEXT,

                               time_pattern_json             TEXT,
                               routine_achievement_json        TEXT,
                               goal_time_distribution_json      TEXT,
                               goal_ranking_json              TEXT,
                               week_over_week_json            TEXT,

                               created_at                   DATETIME NOT NULL,

                               CONSTRAINT uk_weekly_report_user_week UNIQUE (user_id, week_start_date),
                               CONSTRAINT fk_weekly_report_user FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE INDEX idx_weekly_report_user_id ON weekly_report (user_id);