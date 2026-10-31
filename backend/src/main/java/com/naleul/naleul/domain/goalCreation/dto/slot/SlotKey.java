package com.naleul.naleul.domain.goalCreation.dto.slot;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * 인터뷰 슬롯 이름. JSON/AI 쪽에서는 camelCase 문자열("goalStatement")로 주고받는다.
 * 선언 순서 = 기본 질문 순서 (명세 3-1, V52 에서 바뀜).
 * V52: "왜 이루고 싶은지(motivation)"는 묻지 않고, 대신 "어떻게 이루고 싶은지 · 정해 둔 계획(approach)"을 묻는다.
 *      motivation 은 예전 세션 · 앱 호환용으로 남겨 두고 순서 맨 뒤로 보냈다.
 */
public enum SlotKey {
    GOAL_STATEMENT("goalStatement", false),
    METRIC("metric", true),
    DEADLINE("deadline", true),
    APPROACH("approach", true),
    PRACTICE_PREFERENCE("practicePreference", true),
    MOTIVATION("motivation", true);

    private final String key;
    private final boolean skippable;

    SlotKey(String key, boolean skippable) {
        this.key = key;
        this.skippable = skippable;
    }

    @JsonValue
    public String key() {
        return key;
    }

    public boolean isSkippable() {
        return skippable;
    }

    /** "goalStatement" → GOAL_STATEMENT. 모르는 값이면 null (AI가 이상한 값을 줘도 터지지 않게) */
    @JsonCreator
    public static SlotKey fromKey(String key) {
        if (key == null) return null;
        return Arrays.stream(values())
                .filter(k -> k.key.equals(key))
                .findFirst()
                .orElse(null);
    }
}
