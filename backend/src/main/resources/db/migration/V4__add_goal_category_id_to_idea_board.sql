-- dream_board, idea_board 모두 데이터가 없는 상태에서 적용 (2026-06-22 확인)
ALTER TABLE idea_board
    ADD COLUMN goal_category_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_idea_board_goal_category
        FOREIGN KEY (goal_category_id) REFERENCES goal_category(goal_category_id);