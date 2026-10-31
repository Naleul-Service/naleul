-- V20__allow_multiple_answers_per_question.sql

-- 1) 기존 FK 제약 제거 (유니크 인덱스를 지우려면 먼저 이걸 끊어야 함)
ALTER TABLE autobiography_answer DROP FOREIGN KEY FK3kqedgicb4rl4g9usdges8irt;

-- 2) 유니크 인덱스 제거 (질문 하나당 답변 여러 개 허용)
ALTER TABLE autobiography_answer DROP INDEX UKmehb6sij32qob8cbfmuj6oqw6;

-- 3) FK 관계는 유지해야 하므로 새로 추가 (unique 없이, 참조 관계만 강제)
ALTER TABLE autobiography_answer
    ADD CONSTRAINT fk_autobiography_answer_question
        FOREIGN KEY (question_id) REFERENCES autobiography_question(question_id);