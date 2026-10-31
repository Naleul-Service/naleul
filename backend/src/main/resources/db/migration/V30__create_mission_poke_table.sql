CREATE TABLE mission_poke (
                              poke_id BIGINT NOT NULL AUTO_INCREMENT,
                              mission_id BIGINT NOT NULL,
                              sender_id BIGINT NOT NULL,
                              target_user_id BIGINT NOT NULL,
                              poke_date DATE NOT NULL,
                              created_at DATETIME NOT NULL,
                              PRIMARY KEY (poke_id),
                              CONSTRAINT uq_mission_poke_daily UNIQUE (mission_id, sender_id, target_user_id, poke_date),
                              CONSTRAINT fk_mission_poke_mission FOREIGN KEY (mission_id) REFERENCES missions (mission_id),
                              CONSTRAINT fk_mission_poke_sender FOREIGN KEY (sender_id) REFERENCES users (user_id),
                              CONSTRAINT fk_mission_poke_target FOREIGN KEY (target_user_id) REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_mission_poke_target_date ON mission_poke (target_user_id, poke_date);