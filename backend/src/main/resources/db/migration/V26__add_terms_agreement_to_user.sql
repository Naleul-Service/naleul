-- V__add_terms_agreement_to_users.sql

ALTER TABLE users
    ADD COLUMN service_terms_agreed BOOLEAN NOT NULL DEFAULT FALSE COMMENT '서비스 이용약관 동의 여부(필수)',
    ADD COLUMN privacy_agreed BOOLEAN NOT NULL DEFAULT FALSE COMMENT '개인정보 수집·이용 동의 여부(필수)',
    ADD COLUMN marketing_agreed BOOLEAN NOT NULL DEFAULT FALSE COMMENT '마케팅 정보 수신 동의 여부(선택)',
    ADD COLUMN terms_agreed_at DATETIME NULL COMMENT '필수 약관 동의 완료 시각',
    ADD COLUMN onboarding_completed BOOLEAN NOT NULL DEFAULT FALSE COMMENT '온보딩 완료 여부';