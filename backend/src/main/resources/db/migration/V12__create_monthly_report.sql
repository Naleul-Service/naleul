CREATE TABLE monthly_report (
                                monthly_report_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                user_id BIGINT NOT NULL,
                                period_start_date DATE NOT NULL,
                                period_end_date DATE NOT NULL,
                                pattern_type_snapshot VARCHAR(30) NOT NULL,
                                ai_feedback_title TEXT,
                                ai_feedback_content TEXT,
                                time_pattern_json TEXT,
                                weekly_task_count_json TEXT,
                                routine_weekday_json TEXT,
                                routine_ranking_json TEXT,
                                goal_time_distribution_json TEXT,
                                goal_deviation_json TEXT,
                                goal_weekly_series_json TEXT,
                                created_at DATETIME NOT NULL,

                                CONSTRAINT uq_monthly_report_user_period UNIQUE (user_id, period_start_date),
                                CONSTRAINT fk_monthly_report_user
                                    FOREIGN KEY (user_id) REFERENCES users(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;