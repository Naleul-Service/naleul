package com.naleul.naleul.domain.goalCreation.support;

import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto.*;
import com.naleul.naleul.domain.goalCreation.dto.plan.Weekday;
import com.naleul.naleul.domain.goalCreation.enums.DraftField;
import com.naleul.naleul.domain.goalCreation.enums.DraftFieldResult;
import com.naleul.naleul.domain.goalCreation.enums.DraftItemKind;
import com.naleul.naleul.domain.goalCreation.enums.EditDirection;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.naleul.naleul.domain.goalCreation.enums.DraftField.*;

/**
 * AI 초안 ↔ 사용자가 확정한(또는 말로 고치기 직전) 계획을 칸 단위로 비교한다 (V68, 운영 대시보드 "AI 초안 정확도").
 *
 * - 항목은 초안 tempId 로 짝을 맞춘다. AI 에 있고 최종에 없으면 그 항목의 칸이 모두 DELETED,
 *   최종에만 있으면(사용자가 추가) ITEM 한 줄 ADDED.
 * - 초안 화면에서 사용자가 고칠 수 있는 칸만 비교한다 (목표 설명 · 세부 목표 수치 · AI 이유 · 카테고리는 제외).
 *   AI 값과 최종 값이 둘 다 비어 있는 칸은 세지 않는다 (보존율이 빈 칸으로 부풀지 않게).
 * - AUTO: 화면은 목표 기간 · 수치 · 마일스톤 날짜가 바뀌면 마일스톤 날짜 · 단계 수치 · 할 일 날짜를 자동으로 다시 맞춘다
 *   (웹 planUtils.normalizePlan). 그렇게 바뀐 칸은 사용자가 고친 게 아니라서 AUTO 로 따로 둔다.
 * - 값은 화면 표기 문자열로 남긴다 (대시보드 사례 · 확정 뒤 비교에 같은 형식을 쓴다 — {@link #days} 등).
 * DB · 스프링에 의존하지 않는 순수 로직.
 */
public final class DraftPlanComparer {

    private DraftPlanComparer() {
    }

    public record Diff(DraftItemKind kind, String key, String label, DraftField field, DraftFieldResult result,
                       EditDirection direction, Integer delta, String aiValue, String finalValue) {
    }

    private record Dir(EditDirection direction, Integer delta) {
    }

    public static List<Diff> compare(GoalPlanDto ai, GoalPlanDto fin) {
        List<Diff> out = new ArrayList<>();
        if (ai == null || fin == null || ai.goal() == null || fin.goal() == null) return out;
        PlanGoal ag = ai.goal(), fg = fin.goal();
        boolean goalDatesChanged = !Objects.equals(ag.startDate(), fg.startDate()) || !Objects.equals(ag.endDate(), fg.endDate());

        // ── 목표 ──
        String gl = label(fg.title(), ag.title());
        put(out, DraftItemKind.GOAL, "goal", gl, TITLE, text(ag.title()), text(fg.title()), false, null, false);
        put(out, DraftItemKind.GOAL, "goal", gl, EMOJI, text(ag.emoji()), text(fg.emoji()), false, null, false);
        put(out, DraftItemKind.GOAL, "goal", gl, START_DATE, date(ag.startDate()), date(fg.startDate()), false, dateDir(ag.startDate(), fg.startDate()), false);
        put(out, DraftItemKind.GOAL, "goal", gl, END_DATE, date(ag.endDate()), date(fg.endDate()), false, dateDir(ag.endDate(), fg.endDate()), false);
        PlanGoalMetric am = ag.metric(), fm = fg.metric();
        if (am != null || fm != null) {
            put(out, DraftItemKind.GOAL, "goal", gl, METRIC_START, metric(am, true), metric(fm, true), false,
                    numDir(am != null ? am.startValue() : null, fm != null ? fm.startValue() : null), false);
            put(out, DraftItemKind.GOAL, "goal", gl, METRIC_TARGET, metric(am, false), metric(fm, false), false,
                    numDir(am != null ? am.targetValue() : null, fm != null ? fm.targetValue() : null), false);
        }

        // ── 세부 목표 ──
        Map<String, PlanSubGoal> finSub = byKey(fin.subGoals(), PlanSubGoal::tempId);
        Map<String, String> aiSubTitle = titles(ai.subGoals(), PlanSubGoal::tempId, PlanSubGoal::title);
        Map<String, String> finSubTitle = titles(fin.subGoals(), PlanSubGoal::tempId, PlanSubGoal::title);
        Set<String> seenSub = new HashSet<>();
        for (PlanSubGoal a : ai.subGoals()) {
            PlanSubGoal b = a.tempId() != null ? finSub.get(a.tempId()) : null;
            if (b != null) seenSub.add(a.tempId());
            boolean del = b == null;
            String key = keyOf(a.tempId(), "sg", out.size());
            String l = label(b != null ? b.title() : null, a.title());
            put(out, DraftItemKind.SUB_GOAL, key, l, TITLE, text(a.title()), del ? null : text(b.title()), del, null, false);
            put(out, DraftItemKind.SUB_GOAL, key, l, EMOJI, text(a.emoji()), del ? null : text(b.emoji()), del, null, false);
            put(out, DraftItemKind.SUB_GOAL, key, l, DESCRIPTION, text(a.description()), del ? null : text(b.description()), del, null, false);
        }
        int i = 0;
        for (PlanSubGoal b : fin.subGoals()) {
            if (b.tempId() == null || !seenSub.contains(b.tempId())) added(out, DraftItemKind.SUB_GOAL, keyOf(b.tempId(), "new-sg", i), b.title());
            i++;
        }

        // ── 마일스톤 ──
        Map<String, PlanMilestone> finMs = byKey(fin.milestones(), PlanMilestone::tempId);
        Map<String, LocalDate> aiMsDue = new HashMap<>(), finMsDue = new HashMap<>();
        ai.milestones().forEach(m -> { if (m.tempId() != null) aiMsDue.put(m.tempId(), m.dueDate()); });
        fin.milestones().forEach(m -> { if (m.tempId() != null) finMsDue.put(m.tempId(), m.dueDate()); });
        Map<String, String> aiMsTitle = titles(ai.milestones(), PlanMilestone::tempId, PlanMilestone::title);
        Map<String, String> finMsTitle = titles(fin.milestones(), PlanMilestone::tempId, PlanMilestone::title);
        Set<String> seenMs = new HashSet<>();
        String unit = fm != null ? fm.unit() : am != null ? am.unit() : null;
        for (PlanMilestone a : ai.milestones()) {
            PlanMilestone b = a.tempId() != null ? finMs.get(a.tempId()) : null;
            if (b != null) seenMs.add(a.tempId());
            boolean del = b == null;
            String key = keyOf(a.tempId(), "ms", out.size());
            String l = label(b != null ? b.title() : null, a.title());
            put(out, DraftItemKind.MILESTONE, key, l, TITLE, text(a.title()), del ? null : text(b.title()), del, null, false);
            boolean dueAuto = !del && goalDatesChanged
                    && (Objects.equals(b.dueDate(), fg.endDate())
                    || (fg.startDate() != null && Objects.equals(b.dueDate(), fg.startDate().plusDays(1))));
            put(out, DraftItemKind.MILESTONE, key, l, DUE_DATE, date(a.dueDate()), del ? null : date(b.dueDate()), del,
                    del ? null : dateDir(a.dueDate(), b.dueDate()), dueAuto);
            // 단계 수치는 화면에서 고칠 수 없고 날짜 · 목표 수치에 맞춰 자동 계산된다 → 바뀌었으면 항상 AUTO
            put(out, DraftItemKind.MILESTONE, key, l, TARGET_VALUE, number(a.targetValue(), unit), del ? null : number(b.targetValue(), unit), del,
                    del ? null : numDir(a.targetValue(), b.targetValue()), true);
            put(out, DraftItemKind.MILESTONE, key, l, DESCRIPTION, text(a.description()), del ? null : text(b.description()), del, null, false);
        }
        i = 0;
        for (PlanMilestone b : fin.milestones()) {
            if (b.tempId() == null || !seenMs.contains(b.tempId())) added(out, DraftItemKind.MILESTONE, keyOf(b.tempId(), "new-ms", i), b.title());
            i++;
        }

        // ── 루틴 · 일회성 Task ──
        boolean subLinks = ai.subGoals().size() > 1 || fin.subGoals().size() > 1;
        boolean msLinks = !ai.milestones().isEmpty() || !fin.milestones().isEmpty();
        Map<String, PlanTask> finTask = byKey(fin.tasks(), PlanTask::tempId);
        Set<String> seenTask = new HashSet<>();
        for (PlanTask a : ai.tasks()) {
            PlanTask b0 = a.tempId() != null ? finTask.get(a.tempId()) : null;
            PlanTask b = b0 != null && b0.type() == a.type() ? b0 : null;   // 종류가 바뀌면 삭제 + 추가
            if (b != null) seenTask.add(a.tempId());
            boolean del = b == null;
            DraftItemKind kind = a.isRoutine() ? DraftItemKind.ROUTINE : DraftItemKind.ONE_TIME;
            String key = keyOf(a.tempId(), a.isRoutine() ? "rt" : "ot", out.size());
            String l = label(b != null ? b.title() : null, a.title());
            put(out, kind, key, l, TITLE, text(a.title()), del ? null : text(b.title()), del, null, false);
            put(out, kind, key, l, EMOJI, text(a.emoji()), del ? null : text(b.emoji()), del, null, false);
            put(out, kind, key, l, DURATION, minutes(a.durationMinutes()), del ? null : minutes(b.durationMinutes()), del,
                    del ? null : numDir(a.durationMinutes(), b.durationMinutes()), false);
            if (subLinks) {
                put(out, kind, key, l, SUB_GOAL, aiSubTitle.get(a.subGoalTempId()),
                        del ? null : finSubTitle.get(b.subGoalTempId()), del, null, false);
            }
            if (a.isRoutine()) {
                put(out, kind, key, l, DAYS, days(a.routineDays()), del ? null : days(b.routineDays()), del,
                        del ? null : daysDir(a.routineDays(), b.routineDays()), false);
                put(out, kind, key, l, START_TIME, time(a.preferredStartTime()), del ? null : time(b.preferredStartTime()), del,
                        del ? null : timeDir(a.preferredStartTime(), b.preferredStartTime()), false);
                put(out, kind, key, l, HOW_TO, text(a.description()), del ? null : text(b.description()), del, null, false);
            } else {
                if (msLinks) {
                    put(out, kind, key, l, MILESTONE, aiMsTitle.get(a.milestoneTempId()),
                            del ? null : finMsTitle.get(b.milestoneTempId()), del, null, false);
                }
                boolean dueAuto = false, schedAuto = false;
                if (!del) {
                    LocalDate msAi = aiMsDue.get(a.milestoneTempId()), msFin = finMsDue.get(b.milestoneTempId());
                    boolean msMoved = msFin != null && !Objects.equals(msAi, msFin);
                    dueAuto = (goalDatesChanged && (Objects.equals(b.dueDate(), fg.endDate()) || Objects.equals(b.dueDate(), fg.startDate())))
                            || (msMoved && Objects.equals(b.dueDate(), msFin));
                    boolean dueChanged = !Objects.equals(a.dueDate(), b.dueDate());
                    schedAuto = dueChanged && dueAuto
                            && (b.scheduledDate() == null || Objects.equals(b.scheduledDate(), b.dueDate()));
                }
                put(out, kind, key, l, SCHEDULED_DATE, date(a.scheduledDate()), del ? null : date(b.scheduledDate()), del,
                        del ? null : dateDir(a.scheduledDate(), b.scheduledDate()), schedAuto);
                put(out, kind, key, l, DUE_DATE, date(a.dueDate()), del ? null : date(b.dueDate()), del,
                        del ? null : dateDir(a.dueDate(), b.dueDate()), dueAuto);
            }
        }
        i = 0;
        for (PlanTask b : fin.tasks()) {
            if (b.tempId() == null || !seenTask.contains(b.tempId())) {
                added(out, b.isRoutine() ? DraftItemKind.ROUTINE : DraftItemKind.ONE_TIME, keyOf(b.tempId(), "new-t", i), b.title());
            }
            i++;
        }
        return out;
    }

    // ───────────── 결과 한 줄 ─────────────

    private static void put(List<Diff> out, DraftItemKind kind, String key, String label, DraftField field,
                            String a, String b, boolean deleted, Dir dir, boolean autoIfChanged) {
        if (deleted) {
            if (a != null) out.add(new Diff(kind, key, label, field, DraftFieldResult.DELETED, null, null, a, null));
            return;
        }
        if (a == null && b == null) return;   // 둘 다 빈 칸은 세지 않음
        if (Objects.equals(a, b)) {
            out.add(new Diff(kind, key, label, field, DraftFieldResult.KEPT, null, null, a, b));
            return;
        }
        Dir d = dir != null ? dir : new Dir(EditDirection.CHANGED, null);
        out.add(new Diff(kind, key, label, field, autoIfChanged ? DraftFieldResult.AUTO : DraftFieldResult.EDITED,
                d.direction(), d.delta(), a, b));
    }

    private static void added(List<Diff> out, DraftItemKind kind, String key, String title) {
        out.add(new Diff(kind, key, label(title, null), ITEM, DraftFieldResult.ADDED, null, null, null, text(title)));
    }

    // ───────────── 방향 ─────────────

    private static Dir dateDir(LocalDate a, LocalDate b) {
        if (a == null || b == null) return new Dir(EditDirection.CHANGED, null);
        long d = ChronoUnit.DAYS.between(a, b);
        return new Dir(d >= 0 ? EditDirection.LATER : EditDirection.EARLIER, (int) Math.abs(d));
    }

    private static Dir timeDir(LocalTime a, LocalTime b) {
        if (a == null || b == null) return new Dir(EditDirection.CHANGED, null);
        long d = ChronoUnit.MINUTES.between(a, b);
        return new Dir(d >= 0 ? EditDirection.LATER : EditDirection.EARLIER, (int) Math.abs(d));
    }

    private static Dir numDir(Integer a, Integer b) {
        if (a == null || b == null) return new Dir(EditDirection.CHANGED, null);
        return new Dir(b >= a ? EditDirection.MORE : EditDirection.LESS, Math.abs(b - a));
    }

    private static Dir numDir(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) return new Dir(EditDirection.CHANGED, null);
        BigDecimal d = b.subtract(a);
        return new Dir(d.signum() >= 0 ? EditDirection.MORE : EditDirection.LESS,
                d.abs().setScale(0, RoundingMode.HALF_UP).intValue());
    }

    private static Dir daysDir(List<Weekday> a, List<Weekday> b) {
        int na = a == null ? 0 : new HashSet<>(a).size(), nb = b == null ? 0 : new HashSet<>(b).size();
        if (na == nb) return new Dir(EditDirection.CHANGED, 0);
        return new Dir(nb > na ? EditDirection.MORE : EditDirection.LESS, Math.abs(nb - na));
    }

    // ───────────── 화면 표기 (확정 뒤 비교도 같은 형식) ─────────────

    private static final String[] DAY_KO = {"월", "화", "수", "목", "금", "토", "일"};

    public static String text(String s) {
        if (s == null) return null;
        String t = s.strip();
        return t.isEmpty() ? null : (t.length() > 300 ? t.substring(0, 300) : t);
    }

    public static String date(LocalDate d) {
        return d == null ? null : d.toString();
    }

    public static String time(LocalTime t) {
        return t == null ? null : String.format("%02d:%02d", t.getHour(), t.getMinute());
    }

    public static String minutes(Number m) {
        return m == null ? null : m.longValue() + "분";
    }

    /** 요일 → "월·수·금" (월요일부터) */
    public static String days(List<Weekday> days) {
        if (days == null || days.isEmpty()) return null;
        return days.stream().filter(Objects::nonNull).distinct().sorted()
                .map(d -> DAY_KO[d.ordinal()]).collect(Collectors.joining("·"));
    }

    public static String number(BigDecimal v, String unit) {
        if (v == null) return null;
        String n = v.stripTrailingZeros().toPlainString();
        return unit == null || unit.isBlank() ? n : n + unit.strip();
    }

    private static String metric(PlanGoalMetric m, boolean start) {
        if (m == null) return null;
        return number(start ? m.startValue() : m.targetValue(), m.unit());
    }

    // ───────────── 기타 ─────────────

    private static String label(String preferred, String fallback) {
        String l = text(preferred != null ? preferred : fallback);
        if (l == null) return null;
        return l.length() > 100 ? l.substring(0, 100) : l;
    }

    private static String keyOf(String tempId, String prefix, int i) {
        if (tempId != null && !tempId.isBlank()) return tempId.length() > 40 ? tempId.substring(0, 40) : tempId;
        return prefix + "-" + i;
    }

    private static <T> Map<String, T> byKey(List<T> list, Function<T, String> key) {
        Map<String, T> m = new HashMap<>();
        for (T t : list) {
            String k = key.apply(t);
            if (k != null) m.putIfAbsent(k, t);
        }
        return m;
    }

    private static <T> Map<String, String> titles(List<T> list, Function<T, String> key, Function<T, String> title) {
        Map<String, String> m = new HashMap<>();
        for (T t : list) {
            String k = key.apply(t);
            if (k != null) m.putIfAbsent(k, text(title.apply(t)));
        }
        return m;
    }
}
