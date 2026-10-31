CREATE TABLE user_withdrawal (
                                 user_withdrawal_id BIGINT NOT NULL AUTO_INCREMENT,
                                 user_id BIGINT NOT NULL,
                                 user_email VARCHAR(255) NOT NULL,
                                 reason VARCHAR(30) NOT NULL,
                                 detail TEXT NULL,
                                 withdrawn_at DATETIME NOT NULL,
                                 PRIMARY KEY (user_withdrawal_id)
);

CREATE INDEX idx_user_withdrawal_reason ON user_withdrawal (reason);