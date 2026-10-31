CREATE TABLE notifications (
                               id BIGINT AUTO_INCREMENT PRIMARY KEY,
                               user_id BIGINT NOT NULL,
                               category VARCHAR(50) NOT NULL,
                               type VARCHAR(50) NOT NULL,
                               message VARCHAR(500) NOT NULL,
                               action_type VARCHAR(50) NOT NULL,
                               target_id BIGINT NULL,
                               is_read BOOLEAN NOT NULL DEFAULT FALSE,
                               created_at DATETIME NOT NULL,
                               CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(user_id),
                               INDEX idx_notifications_user_created (user_id, created_at DESC)
);

CREATE TABLE notification_settings (
                                       id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                       user_id BIGINT NOT NULL UNIQUE,
                                       mission_alert_enabled BOOLEAN NOT NULL DEFAULT TRUE,
                                       mission_certification_enabled BOOLEAN NOT NULL DEFAULT TRUE,
                                       wishlist_enabled BOOLEAN NOT NULL DEFAULT TRUE,
                                       mission_progress_enabled BOOLEAN NOT NULL DEFAULT TRUE,
                                       mission_edit_enabled BOOLEAN NOT NULL DEFAULT FALSE,
                                       badge_enabled BOOLEAN NOT NULL DEFAULT FALSE,
                                       poke_enabled BOOLEAN NOT NULL DEFAULT TRUE,
                                       CONSTRAINT fk_notification_settings_user FOREIGN KEY (user_id) REFERENCES users(user_id)
);