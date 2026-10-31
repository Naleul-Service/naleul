package com.naleul.naleul.domain.work.support;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 여러 줄로 적은 할 일 → Task 이름 목록 (V52, 브레인덤프처럼 붙여 넣기).
 * 줄 앞의 머리표("- ", "* ", "• ", "· ", "1. ", "1) ", "[ ] ", "[x] ", "☐ ")는 지우고, 빈 줄 · 같은 이름은 한 번만.
 * AI 없이 바로 추가해서 빠르다 (AI 로 나눠 주는 건 기존 브레인덤프).
 */
public final class WorkTaskTextParser {

    public static final int NAME_MAX = 100;

    private static final Pattern BULLET = Pattern.compile(
            "^\\s*(?:[-*•·▪◦‣]\\s*|\\d{1,3}[.)]\\s*|\\[\\s?[xX ]?\\s?]\\s*|[☐☑✅✔]\\s*)+");

    private WorkTaskTextParser() {
    }

    public static List<String> parse(String text, List<String> lines) {
        List<String> raw = new ArrayList<>();
        if (text != null) raw.addAll(List.of(text.split("\\R")));
        if (lines != null) raw.addAll(lines);
        Set<String> names = new LinkedHashSet<>();
        for (String line : raw) {
            if (line == null) continue;
            String name = BULLET.matcher(line).replaceFirst("").strip();
            if (name.isEmpty()) continue;
            if (name.length() > NAME_MAX) name = name.substring(0, NAME_MAX);
            names.add(name);
        }
        return new ArrayList<>(names);
    }
}
