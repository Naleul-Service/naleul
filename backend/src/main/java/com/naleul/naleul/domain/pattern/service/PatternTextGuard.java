package com.naleul.naleul.domain.pattern.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 가 다듬은 문장을 써도 되는지 검사 (명세 4 · 5장 "출력 검증").
 * 하나라도 어기면 템플릿 문장을 쓴다.
 *  1. 숫자(12, 93, 19:00, 1.4)가 모두 그대로 있어야 한다 (순서 무관, 개수까지 같게)
 *  2. **강조** 부분이 글자 그대로 같아야 한다 (이름·시각·요일이 대부분 강조 안에 있음)
 *  3. 요일 단어(월요일~일요일)가 그대로 있어야 한다
 *  4. 너무 길어지면 안 된다 (원문의 1.5배 또는 +30자 중 큰 값, 최대 200자)
 */
public final class PatternTextGuard {

    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.:]\\d+)?");
    private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern DAY = Pattern.compile("[월화수목금토일]요일");
    static final int MAX_LENGTH = 200;

    private PatternTextGuard() {
    }

    public static boolean accept(String original, String polished) {
        if (original == null || polished == null || polished.isBlank()) return false;
        int limit = Math.min(MAX_LENGTH, Math.max((int) (original.length() * 1.5), original.length() + 30));
        if (polished.length() > limit) return false;
        if (count(polished, "**") % 2 != 0) return false;
        return all(NUMBER, original).equals(all(NUMBER, polished))
                && group(BOLD, original).equals(group(BOLD, polished))
                && all(DAY, original).equals(all(DAY, polished));
    }

    private static List<String> all(Pattern p, String s) {
        List<String> out = new ArrayList<>();
        Matcher m = p.matcher(s);
        while (m.find()) out.add(m.group());
        Collections.sort(out);
        return out;
    }

    private static List<String> group(Pattern p, String s) {
        List<String> out = new ArrayList<>();
        Matcher m = p.matcher(s);
        while (m.find()) out.add(m.group(1));
        Collections.sort(out);
        return out;
    }

    private static int count(String s, String sub) {
        int n = 0;
        for (int i = s.indexOf(sub); i >= 0; i = s.indexOf(sub, i + sub.length())) n++;
        return n;
    }
}
