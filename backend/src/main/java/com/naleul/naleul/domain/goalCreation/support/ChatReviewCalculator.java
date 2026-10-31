package com.naleul.naleul.domain.goalCreation.support;

import com.naleul.naleul.domain.goalCreation.dto.slot.*;
import com.naleul.naleul.domain.goalCreation.enums.ReviewField;
import com.naleul.naleul.domain.goalCreation.enums.ReviewResult;
import com.naleul.naleul.domain.user.enums.PlanningStyle;

import java.math.BigDecimal;
import java.util.*;

/**
 * 정리 화면 항목별 결과 (V67, 운영 대시보드 "AI 채팅 정리 정확도").
 * 대화가 끝난 순간의 AI 정리값과 처음 "계획 만들기"를 누른 순간의 값을 비교한다.
 *  - AI 가 채운 항목: 값이 같으면 KEPT, 다르면(지운 것 포함) EDITED
 *  - AI 가 비운 항목: 사용자가 채웠으면 ADDED, 아니면 EMPTY
 *  - 실행 성향: 화면에 AI 추천으로 보인 성향과 다른 성향을 골라 계획을 만들면 EDITED
 * 같은 값으로 저장하거나 바꿨다 되돌린 경우는 값이 같으므로 KEPT. DB · 스프링에 의존하지 않는 순수 로직.
 */
public final class ChatReviewCalculator {

    private ChatReviewCalculator() {
    }

    public record Row(ReviewField field, boolean aiFilled, ReviewResult result) {
    }

    /**
     * @param aiStyle     정리 화면에 "AI 추천"으로 보인 성향 (null 이면 PLANNER — 화면 기본값과 같게)
     * @param chosenStyle 사용자가 직접 고른 성향 (안 골랐으면 null = 추천 그대로)
     */
    public static List<Row> compare(GoalSlots ai, GoalSlots now, PlanningStyle aiStyle, PlanningStyle chosenStyle) {
        List<Row> rows = new ArrayList<>();
        for (ReviewField f : ReviewField.values()) {
            if (f == ReviewField.PLANNING_STYLE) {
                PlanningStyle shown = aiStyle != null ? aiStyle : PlanningStyle.PLANNER;
                boolean changed = chosenStyle != null && chosenStyle != shown;
                rows.add(new Row(f, true, changed ? ReviewResult.EDITED : ReviewResult.KEPT));
                continue;
            }
            Slot<?> a = ai.get(f.askedSlot());
            Slot<?> b = now.get(f.askedSlot());
            boolean aiFilled = a != null && a.isFilled() && a.value() != null;
            boolean nowFilled = b != null && b.isFilled() && b.value() != null;
            ReviewResult result;
            if (aiFilled) result = nowFilled && same(f.askedSlot(), a.value(), b.value()) ? ReviewResult.KEPT : ReviewResult.EDITED;
            else result = nowFilled ? ReviewResult.ADDED : ReviewResult.EMPTY;
            rows.add(new Row(f, aiFilled, result));
        }
        return rows;
    }

    static boolean same(SlotKey key, Object a, Object b) {
        if (a instanceof String sa && b instanceof String sb) return sa.strip().equals(sb.strip());
        if (a instanceof MetricSlot ma && b instanceof MetricSlot mb) {
            return text(ma.name()).equals(text(mb.name())) && text(ma.unit()).equals(text(mb.unit()))
                    && num(ma.currentValue(), mb.currentValue()) && num(ma.targetValue(), mb.targetValue());
        }
        if (a instanceof PracticePreferenceSlot pa && b instanceof PracticePreferenceSlot pb) {
            // 요일은 순서와 상관없이 (앱이 월→일로 정렬해 다시 보내도 같은 값)
            return set(pa.preferredDays()).equals(set(pb.preferredDays()))
                    && Objects.equals(list(pa.preferredTimeRanges()), list(pb.preferredTimeRanges()))
                    && text(pa.unavailableNote()).equals(text(pb.unavailableNote()))
                    && text(pa.workStyleAnswer()).equals(text(pb.workStyleAnswer()));
        }
        return Objects.equals(a, b);
    }

    private static String text(String s) {
        return s == null ? "" : s.strip();
    }

    private static boolean num(BigDecimal x, BigDecimal y) {
        if (x == null || y == null) return x == y;
        return x.compareTo(y) == 0;
    }

    private static <T> Set<T> set(List<T> l) {
        return l == null ? Set.of() : new HashSet<>(l);
    }

    private static <T> List<T> list(List<T> l) {
        return l == null ? List.of() : l;
    }
}
