package com.naleul.naleul.domain.pattern.client;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 로컬·테스트용 (goal.ai.client=fake): 문장을 그대로 돌려준다 → aiStatus 는 TEMPLATE 로 남는다 */
@Component
@ConditionalOnProperty(prefix = "goal.ai", name = "client", havingValue = "fake")
public class FakePatternAiClient implements PatternAiClient {

    @Override
    public Map<String, String> polish(String context, List<Text> texts) {
        Map<String, String> out = new LinkedHashMap<>();
        texts.forEach(t -> out.put(t.id(), t.text()));
        return out;
    }
}
