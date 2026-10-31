CREATE TABLE goal_completion_report (
                                        goal_completion_report_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                        user_id BIGINT NOT NULL,
                                        goal_category_id BIGINT NOT NULL,
                                        goal_category_name_snapshot VARCHAR(255) NOT NULL,
                                        achievement_snapshot TEXT,
                                        achievement_image_url_snapshot VARCHAR(255),
                                        start_date DATE NOT NULL,
                                        end_date DATE NOT NULL,
                                        days_together BIGINT NOT NULL,
                                        total_minutes BIGINT NOT NULL,
                                        execution_count INT NOT NULL,
                                        achievement_rate DOUBLE NOT NULL,
                                        time_pattern_json TEXT,
                                        weekday_pattern_json TEXT,
                                        period_pattern_json TEXT,
                                        general_category_time_json TEXT,
                                        general_category_ranking_json TEXT,
                                        routine_ranking_json TEXT,
                                        general_category_achievement_json TEXT,
                                        created_at DATETIME NOT NULL,

                                        CONSTRAINT uq_goal_completion_report_goal_category UNIQUE (goal_category_id),
                                        CONSTRAINT fk_goal_completion_report_user
                                            FOREIGN KEY (user_id) REFERENCES users(user_id),
                                        CONSTRAINT fk_goal_completion_report_goal_category
                                            FOREIGN KEY (goal_category_id) REFERENCES goal_category(goal_category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;