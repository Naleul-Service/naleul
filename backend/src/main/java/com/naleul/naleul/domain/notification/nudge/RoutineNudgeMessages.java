package com.naleul.naleul.domain.notification.nudge;

import com.naleul.naleul.domain.routine.util.RoutinePracticeStats.Stats;

/**
 * 루틴 알림 문구 (2026-10) — "할 시간이에요" 대신 실천률 · 연속 기록 같은 구체적인 숫자로.
 */
public final class RoutineNudgeMessages {

    private RoutineNudgeMessages() {}

    /** 기록이 이만큼은 있어야 실천률을 말한다 */
    static final int MIN_HISTORY = 3;
    /** 이 실천률 이상이면 "잘 지켜 온 루틴" */
    public static final int HIGH_RATE = 70;

    public record Message(String title, String body) {}

    public static String quoted(String emoji, String name) {
        String n = (emoji == null || emoji.isBlank()) ? name : emoji + " " + name;
        return "'" + n + "'";
    }

    /** 받침이 있으면 "을", 없으면 "를" (따옴표·공백은 건너뛰고 마지막 글자로 판단) */
    static String eulReul(String word) {
        for (int i = word.length() - 1; i >= 0; i--) {
            char c = word.charAt(i);
            if (c == '\'' || Character.isWhitespace(c)) continue;
            if (c >= 0xAC00 && c <= 0xD7A3) return (c - 0xAC00) % 28 == 0 ? "를" : "을";
            return "을(를)";
        }
        return "을(를)";
    }

    /** 루틴 시작 시각 알림 (TASK_START) */
    public static Message routineStart(String name, Stats s, boolean top) {
        if (s == null || s.total() < MIN_HISTORY) {
            return new Message("⏰ 루틴 시간", "지금은 " + name + " 루틴을 할 시간이에요");
        }
        if (top) {
            return new Message("⏰ 루틴 시간",
                    "지금은 실천률이 " + s.rate() + "%로 가장 높은 " + name + " 루틴을 할 시간이에요. 실천률을 지켜봐요");
        }
        return new Message("⏰ 루틴 시간",
                "지금은 " + name + " 루틴을 할 시간이에요. 실천률 " + s.rate() + "% → 오늘 하면 " + s.rateIfDoneToday() + "%");
    }

    /** 어제 이 시각에 했는데 오늘은 아직 */
    public static Message yesterdayTime(String name, String hhmm, Stats s) {
        String head = "어제 " + hhmm + "에 " + name + eulReul(name) + " 했어요.";
        if (s != null && s.streak() >= 2) {
            return new Message("🔁 어제 이 시간에 했어요", head + " 연속 " + s.streak() + "일 기록을 지켜봐요");
        }
        if (s != null && s.total() >= MIN_HISTORY) {
            return new Message("🔁 어제 이 시간에 했어요",
                    head + " 실천률 " + s.rate() + "% — 오늘도 하면 " + s.rateIfDoneToday() + "%로 올라가요");
        }
        return new Message("🔁 어제 이 시간에 했어요", head + " 오늘도 이어가 볼까요?");
    }

    /** 실천률이 높은 루틴을 계획 시간에 못 했을 때 */
    public static Message rateReschedule(String name, Stats s) {
        return new Message("📈 실천률을 지켜요",
                "실천률 " + s.rate() + "%로 잘 지켜 온 " + name + " 루틴, 오늘은 아직이에요. 계획 시간을 바꿔서 이어가 볼까요?");
    }
}
