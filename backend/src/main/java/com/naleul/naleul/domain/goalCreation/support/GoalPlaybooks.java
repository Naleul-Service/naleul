package com.naleul.naleul.domain.goalCreation.support;

import com.naleul.naleul.domain.goalCategory.kind.GoalKind;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 카테고리별 목표 설계 가이드 (resources/ai/goal-creation/playbooks.txt).
 *
 * 왜 프롬프트 하나에 다 넣지 않고 골라서 붙이나
 *  - 다이어트 계획에 금연·독서 가이드까지 섞이면 AI 가 엉뚱한 예시를 끌어와요.
 *  - 고른 카테고리 가이드만 붙이면 토큰도 줄고, "운동이면 세트·횟수까지" 같은 기대치가 분명해져요.
 *
 * 2단계(운동, 다이어트…) 가이드가 있으면 그걸, "기타"나 없는 경우엔 1단계(건강, 학습…) 가이드를 쓴다.
 */
public class GoalPlaybooks {

    private static final String RESOURCE = "ai/goal-creation/playbooks.txt";
    private static final String INTERVIEW_PREFIX = "꼭 알아야 할 것:";

    private final Map<String, String> sections;

    public GoalPlaybooks() {
        this(read());
    }

    GoalPlaybooks(String text) {
        this.sections = parse(text);
    }

    /** 계획 요청에 붙일 가이드 전문. 종류가 없으면 null */
    public String forPlan(GoalKind kind) {
        if (kind == null) return null;
        if (kind.subType() != null && !kind.subType().isCustom()) {
            String sub = sections.get(kind.subType().name());
            if (sub != null) return sub;
        }
        return sections.get(kind.type().name());
    }

    /** 인터뷰에서 꼭 물어볼 것 한 줄 ("꼭 알아야 할 것: …"). 없으면 null */
    public String forInterview(GoalKind kind) {
        String plan = forPlan(kind);
        if (plan == null) return null;
        return plan.lines()
                .filter(l -> l.startsWith(INTERVIEW_PREFIX))
                .map(l -> l.substring(INTERVIEW_PREFIX.length()).strip())
                .findFirst()
                .orElse(null);
    }

    static Map<String, String> parse(String text) {
        Map<String, String> result = new HashMap<>();
        String key = null;
        StringBuilder body = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            if (line.startsWith("### ")) {
                if (key != null) result.put(key, body.toString().strip());
                key = line.substring(4).strip();
                body.setLength(0);
            } else if (!line.startsWith("#") && key != null) {
                body.append(line).append('\n');
            }
        }
        if (key != null) result.put(key, body.toString().strip());
        return result;
    }

    private static String read() {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            // 빠진 채 배포되면 기동 시점에 바로 실패 (조용히 가이드 없이 돌지 않게)
            throw new UncheckedIOException("목표 가이드 리소스 로드 실패: " + RESOURCE, e);
        }
    }
}
