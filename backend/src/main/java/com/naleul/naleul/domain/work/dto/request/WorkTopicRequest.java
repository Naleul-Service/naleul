package com.naleul.naleul.domain.work.dto.request;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 주제 만들기 · 고치기 (V52) — 만들 때는 name 필수, 고칠 때는 보낸 값만
 * V53: dueDate 주제 마감 (선택) · clearDueDate 마감 지우기 · completed 끝냈어요(true) / 다시 열기(false)
 * 예) { "name": "포트폴리오", "dueDate": "2026-10-31" }
 */
public record WorkTopicRequest(
        @Size(max = 30, message = "세부 목표 이름은 30자 이하로 입력해 주세요.")
        String name,

        @Size(max = 16)
        String emoji,

        LocalDate dueDate,

        Boolean clearDueDate,

        Boolean completed
) {
    /** dueDate 없이 쓰던 예전 호출부 · 테스트 호환 */
    public WorkTopicRequest(String name, String emoji) {
        this(name, emoji, null, null, null);
    }
}
