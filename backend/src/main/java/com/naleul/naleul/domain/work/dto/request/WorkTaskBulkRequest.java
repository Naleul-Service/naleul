package com.naleul.naleul.domain.work.dto.request;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * 브레인덤프처럼 여러 개 한 번에 (V52)
 * - text 에 여러 줄로 적거나 (줄마다 하나 · "- ", "• ", "1. ", "[ ] " 같은 머리표는 지운다), lines 로 보내도 된다 (둘 다면 합친다)
 * - topicId · date · dueDate 는 모두에게 똑같이 (선택)
 * 예) { "text": "- 와이어프레임\n- API 명세\n- QA 시트", "topicId": 3 }
 */
public record WorkTaskBulkRequest(
        @Size(max = 5000)
        String text,

        List<String> lines,

        Long topicId,

        LocalDate date,

        LocalDate dueDate
) {
}
