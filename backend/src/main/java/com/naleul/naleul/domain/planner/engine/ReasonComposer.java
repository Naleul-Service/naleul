package com.naleul.naleul.domain.planner.engine;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * 이유 코드 → 사용자에게 보여줄 한 문장 (명세 6-5).
 *
 *  "{중요도 이유} {시간대 설명} {HH:mm}에 배치했어요."
 *  예) "마감이 1일 남아서 최근 4주 완료율이 82%로 높은 21시대인 21:30에 배치했어요."
 *      "마감이 여유 있어서 15:00에 배치했어요. 잘 지키는 시간은 마감이 가까운 일에 먼저 남겨 뒀어요."
 *      "남은 빈 시간 중 완료율이 가장 높은 19시대인 19:10에 배치했어요."
 *      "어제 못 한 일이라(2번째 이월) 남은 빈 시간인 20:10에 배치했어요."
 */
public final class ReasonComposer {

    private ReasonComposer() {
    }

    public static String cause(ReasonCode code, int value) {
        if (code == null) return null;
        return switch (code) {
            case OVERDUE -> "마감이 지난 일이라";
            case DEADLINE_TODAY -> "오늘이 마감이라";
            case DEADLINE_SOON -> "마감이 " + value + "일 남아서";
            case CARRIED_OVER -> value <= 1 ? "어제 못 한 일이라" : "어제 못 한 일이라(" + value + "번째 이월)";
            case APPOINTMENT -> "약속 일정이라";
            case MILESTONE_SOON -> value == 0 ? "마일스톤 마감이 오늘이라" : "마일스톤 마감이 " + value + "일 남아서";
            default -> null;
        };
    }

    /** "…인" 으로 끝나는 시간대 설명 (뒤에 HH:mm 이 붙음) */
    public static String slotPhrase(ReasonCode code, int hour, int percent, String window, boolean hasCause) {
        return switch (code) {
            // 구체적인 숫자("n개 중 m개")는 뒤 근거 문장에서 — 여기 퍼센트는 보정된 값이라 실제 기록과 달라 보여서 뺐다
            case HIGH_EFFICIENCY -> "평소 잘 지키는 " + hour + "시대인";
            case BEST_REMAINING -> "남은 빈 시간 중 완료율이 가장 높은 " + hour + "시대인";
            case EARLY_SLOT -> "일찍 끝낼 수 있게 이른 시간인";
            // 2026-10-24: "가벼운 일이라 집중이 덜 필요한" → 일의 가치를 평가하는 말 대신 사실(마감 여유)만 말한다.
            //  중요도 LOW = 우선순위·마감·이월 점수가 35 미만 → 실제 근거는 "마감이 멀다"이지 "일이 가볍다"가 아님.
            //  앞에 이유("마감이 7일 남아서" 등)가 이미 있으면 붙이지 않는다. 뒤 문장(LOW_LOAD_NOTE)은 explain 에서 붙인다
            case LOW_LOAD_SLOT -> hasCause ? "" : "마감이 여유 있어서";
            case PREFERRED_TIME -> "목표를 만들 때 고른 선호 시간(" + window + ")인";
            case DEFAULT_CURVE -> "아직 기록이 적어 보통 집중이 잘 되는 시간대인";
            case ONLY_SLOT -> "남은 빈 시간인";
            default -> "";
        };
    }

    public static String compose(String cause, String slotPhrase, LocalDateTime start, Integer adjustedFrom) {
        StringBuilder sb = new StringBuilder();
        if (cause != null) sb.append(cause).append(' ');
        if (slotPhrase != null && !slotPhrase.isBlank()) sb.append(slotPhrase).append(' ');
        sb.append(String.format("%02d:%02d에 배치했어요.", start.getHour(), start.getMinute()));
        if (adjustedFrom != null) {
            sb.append(" 지난 기록을 보고 ").append(adjustedFrom).append("분에서 시간을 늘렸어요.");
        }
        return sb.toString();
    }

    /**
     * 나눠 넣은 Task 의 이유.
     *  예) "한 번에 할 연속된 빈 시간이 없어 09:00–12:00, 13:00–14:00 두 번에 나눠 배치했어요."
     */
    public static String split(String cause, java.util.List<com.naleul.naleul.domain.planner.support.DateTimeRange> segments,
                               Integer adjustedFrom) {
        StringBuilder sb = new StringBuilder();
        if (cause != null) sb.append(cause).append(' ');
        sb.append("한 번에 할 연속된 빈 시간이 없어 ");
        for (int i = 0; i < segments.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(hm(segments.get(i).start())).append('–').append(hm(segments.get(i).end()));
        }
        sb.append(' ').append(segments.size() == 2 ? "두" : segments.size() == 3 ? "세" : String.valueOf(segments.size()))
                .append(" 번에 나눠 배치했어요.");
        if (adjustedFrom != null) sb.append(" 지난 기록을 보고 ").append(adjustedFrom).append("분에서 시간을 늘렸어요.");
        return sb.toString();
    }

    private static String hm(LocalDateTime t) {
        return String.format("%02d:%02d", t.getHour(), t.getMinute());
    }

    /**
     * 내 기록 근거 문장 (첫 문장 뒤에 붙음). 근거가 없으면 빈 문자열.
     *  예) "최근 4주 19–21시에 계획한 일 12개 중 10개를 해냈어요(평소 64%보다 높아요). '다이어트' 일은 이 시간에 5번 중 5번 했어요."
     *      "잘 지키는 시간은 마감이 가까운 일에 먼저 남겨 두고, 최근 4주 15시대에 계획한 일 10개 중 4개를 해낸 이 시간에 넣었어요."
     *      "평소 블록을 이 시간대로 자주 옮기셔서 먼저 골랐어요."
     */
    public static String evidence(PatternEvidence.Counts slot, Integer overallPercent, String hourLabel,
                                  boolean lowImportance, String goalName, PatternEvidence.Counts goalSlot,
                                  boolean moveHabit) {
        StringBuilder sb = new StringBuilder();
        if (slot != null && slot.planned() >= MIN_EVIDENCE) {
            if (lowImportance && overallPercent != null && slot.percent() < overallPercent) {
                sb.append(LOW_LOAD_NOTE_PREFIX).append("두고, 최근 4주 ").append(hourLabel).append("에 계획한 일 ")
                        .append(slot.planned()).append("개 중 ").append(slot.done()).append("개를 해낸 이 시간에 넣었어요.");
            } else {
                sb.append("최근 4주 ").append(hourLabel).append("에 계획한 일 ").append(slot.planned())
                        .append("개 중 ").append(slot.done()).append("개를 해냈어요");
                if (overallPercent != null && slot.percent() >= overallPercent + 10) {
                    sb.append("(평소 ").append(overallPercent).append("%보다 높아요)");
                }
                sb.append('.');
            }
        }
        if (goalName != null && goalSlot != null && goalSlot.planned() >= MIN_EVIDENCE && goalSlot.percent() >= 60) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append('\'').append(goalName).append("' 일은 이 시간에 ").append(goalSlot.planned()).append("번 중 ")
                    .append(goalSlot.done()).append("번 했어요.");
        }
        if (moveHabit) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append("평소 블록을 이 시간대로 자주 옮기셔서 먼저 골랐어요.");
        }
        return sb.toString();
    }

    /** 마감이 여유 있는 일을 덜 지키는 시간에 둔 이유 (기록 근거가 없을 때 그대로 붙인다) */
    static final String LOW_LOAD_NOTE_PREFIX = "잘 지키는 시간은 마감이 가까운 일에 먼저 남겨 ";
    public static final String LOW_LOAD_NOTE = LOW_LOAD_NOTE_PREFIX + "뒀어요.";

    /** 근거로 말하려면 최소 이만큼은 기록이 있어야 한다 */
    static final int MIN_EVIDENCE = 3;

    /** 21:30~22:30 → "21–23시", 19:00~19:50 → "19시대" */
    public static String hourLabel(LocalDateTime start, LocalDateTime end) {
        int first = start.getHour();
        LocalDateTime last = end.minusMinutes(1);
        int lastHour = last.toLocalDate().isAfter(start.toLocalDate()) ? 23 : last.getHour();
        return lastHour <= first ? first + "시대" : first + "–" + (lastHour + 1) + "시";
    }

    /** 다른 날로 옮겨 배치했을 때 앞에 붙이는 문장 (예: "오늘은 빈 시간이 부족해 금요일로 옮겼어요. ") */
    public static String movedPrefix(LocalDate original, LocalDate moved, LocalDate today) {
        String from = original.equals(today) ? "오늘은" : dayName(original) + "에는";
        return from + " 빈 시간이 부족해 " + dayName(moved) + "로 옮겼어요. ";
    }

    private static String dayName(LocalDate d) {
        return d.getMonthValue() + "/" + d.getDayOfMonth() + "(" + d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN) + ")";
    }
}
