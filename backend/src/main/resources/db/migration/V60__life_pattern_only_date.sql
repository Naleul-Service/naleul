-- 기본 생활 패턴 "이날만" (2026-10, 캘린더 빈 칸 → 기본 생활 패턴 추가 → 이날만)
-- NULL = 매주 반복 (기존 동작), 값이 있으면 그 날짜에만 고정 블록이 생긴다.
ALTER TABLE life_pattern ADD COLUMN only_date DATE NULL AFTER days_mask;
