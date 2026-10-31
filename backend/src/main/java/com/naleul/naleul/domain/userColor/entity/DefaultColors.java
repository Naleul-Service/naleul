package com.naleul.naleul.domain.userColor.entity;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class DefaultColors {

    private DefaultColors() {}

    public static final List<String> CODES = List.of(
            "#F26B27", "#F39C12",
            "#4EAE4D", "#20AD9A", "#1E7A90",
            "#3465A4", "#734ED0", "#C13A9A",
            "#F25080", "#FDC68B",
            "#FEE180", "#B9E7B8", "#9AE3DB",
            "#8BC1DF", "#93ADDF", "#BEA9ED",
            "#EAA5DF", "#F298B3"
    );

    /**
     * 더 이상 고를 수 없는 기본 색 (2026-10 빨간색 제외 — 목표를 이뤄도 "못 한 것처럼" 보여서).
     * 이미 이 색을 쓰는 목표는 그대로 두고, 색 목록 · 자동 배정에서만 뺀다.
     */
    public static final Set<String> RETIRED = Set.of("#EB4848", "#FBA5A5");

    public static boolean isRetired(UserColor color) {
        return color != null && color.isDefault() && color.getColorCode() != null
                && RETIRED.contains(color.getColorCode().toUpperCase(Locale.ROOT));
    }
}