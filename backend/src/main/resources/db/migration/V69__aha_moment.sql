-- Aha-moment 측정 (2026-10-25) — 운영 대시보드 전용, 사용자 화면 · 앱 변화 없음
-- 기준: 가입 후 7일 안에 ① 목표 2개 생성 ② Task 3개 이상 완료(루틴 · 일반) ③ 서로 다른 3일 이상 방문
-- 그동안 가입 시각 · 목표를 만든 시각이 없어서 V69 이후 가입자부터 잰다 (예전 행은 NULL → 집계에서 빠짐)

ALTER TABLE users
    ADD COLUMN created_at DATETIME(6) NULL COMMENT '가입 시각 (V69부터, 그 전 가입자는 NULL)',
    ADD KEY idx_users_created (created_at);

ALTER TABLE goal_category
    ADD COLUMN created_at DATETIME(6) NULL COMMENT '목표를 만든 시각 (V69부터, 그 전 목표는 NULL)',
    ADD KEY idx_gc_user_created (user_id, created_at);

-- 방문한 날: 앱 실행만으로는 세지 않고, 오늘의 TimeBlock · Task 화면(홈 오늘 · 타임테이블)을 본 날만 (하루 1줄)
CREATE TABLE user_visit_day (
    user_id     BIGINT      NOT NULL,
    visit_date  DATE        NOT NULL COMMENT '한국 날짜',
    first_at    DATETIME(6) NOT NULL COMMENT '그날 처음 본 시각 (서버 시간)',
    source      VARCHAR(10) NOT NULL COMMENT 'HOME 홈 오늘 · TIMETABLE 타임테이블 (그날 처음 본 화면)',
    PRIMARY KEY (user_id, visit_date),
    KEY idx_uvd_date (visit_date),
    CONSTRAINT fk_uvd_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
