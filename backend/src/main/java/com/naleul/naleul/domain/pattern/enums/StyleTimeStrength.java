package com.naleul.naleul.domain.pattern.enums;

/** 실행 스타일 ① 시간대 강점 (명세 4 · 3-1) */
public enum StyleTimeStrength {
    MORNING("아침에 강한"),
    DAYTIME("낮에 강한"),
    EVENING("저녁에 강한"),
    EVEN("시간을 가리지 않는");

    private final String modifier;

    StyleTimeStrength(String modifier) {
        this.modifier = modifier;
    }

    public String modifier() {
        return modifier;
    }
}
