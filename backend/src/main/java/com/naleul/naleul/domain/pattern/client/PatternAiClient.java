package com.naleul.naleul.domain.pattern.client;

import java.util.List;
import java.util.Map;

/**
 * 패턴 리포트 문장 다듬기 (명세 4 · 5장). AI 는 숫자를 만들지 않고 문장만 다듬는다.
 * 구현: FakePatternAiClient (goal.ai.client=fake), ClaudePatternAiClient (claude 또는 설정 없음)
 */
public interface PatternAiClient {

    /**
     * @param context 유형 제목 등 문맥 (예: "저녁에 강한 꾸준형")
     * @return id → 다듬은 문장. 실패하면 예외 (호출하는 쪽이 템플릿 문장으로 대신함)
     */
    Map<String, String> polish(String context, List<Text> texts);

    record Text(String id, String text) {
    }
}
