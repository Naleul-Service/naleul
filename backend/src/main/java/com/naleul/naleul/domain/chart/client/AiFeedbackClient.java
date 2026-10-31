package com.naleul.naleul.domain.chart.client;


import com.naleul.naleul.domain.chart.dto.AiFeedbackDto;
import com.naleul.naleul.domain.user.enums.UserPatternType;

public interface AiFeedbackClient {
    /**
     * @param patternType 유저가 선택한 유형
     * @param statsSummary 이번 기간 통계를 요약한 텍스트 (프롬프트 재료)
     * @param periodLabel 프롬프트에 들어갈 기간 표현. 주간 리포트는 "이번 주", 월간 리포트는 "이번 달" 등.
     *                    ⚠️ 예전엔 "이번 주"가 프롬프트에 하드코딩돼 있어서, 월간 리포트를 생성할 때도
     *                    AI한테 "이번 주 통계"라고 잘못 알려주는 버그가 있었음. 이 파라미터로 고정.
     */
    AiFeedbackDto generateFeedback(UserPatternType patternType, String statsSummary, String periodLabel);
}