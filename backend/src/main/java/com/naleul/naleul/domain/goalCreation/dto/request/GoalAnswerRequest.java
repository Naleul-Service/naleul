package com.naleul.naleul.domain.goalCreation.dto.request;

import com.naleul.naleul.domain.goalCreation.enums.AnswerSource;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 답변: { "content": "78인데 68까지", "skip": false } / 건너뛰기: { "content": null, "skip": true } */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoalAnswerRequest {

    @Size(max = 500, message = "답변은 500자 이하로 입력해주세요.")
    private String content;

    private boolean skip;

    /** V67: CHIP(칩을 눌러 답함) · TYPED(직접 씀). 없으면 서버가 칩 글자와 비교해 추정 */
    private AnswerSource answerSource;

    /** V67: 고른 칩 번호 (0부터, CHIP 일 때만) */
    private Integer chipIndex;
}
