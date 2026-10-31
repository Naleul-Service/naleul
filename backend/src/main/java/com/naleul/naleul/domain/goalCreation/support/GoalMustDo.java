package com.naleul.naleul.domain.goalCreation.support;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * "이 목표를 위해 꼭 하고 싶은 일" 정리 (V42).
 * 예) 다이어트 → ["헬스", "식단 기록"]
 * 앞뒤 공백 제거 · 빈 값 제외 · 같은 말 하나만 · 각 30자 · 최대 5개.
 */
public final class GoalMustDo {

    public static final int MAX_ITEMS = 5;
    public static final int MAX_LENGTH = 30;

    private GoalMustDo() {
    }

    public static List<String> normalize(List<String> raw) {
        if (raw == null) return List.of();
        Set<String> seen = new LinkedHashSet<>();
        for (String s : raw) {
            if (s == null) continue;
            String t = s.strip().replaceAll("\\s{2,}", " ");
            if (t.isEmpty()) continue;
            if (t.length() > MAX_LENGTH) t = t.substring(0, MAX_LENGTH);
            seen.add(t);
            if (seen.size() >= MAX_ITEMS) break;
        }
        return new ArrayList<>(seen);
    }
}
