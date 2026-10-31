package com.naleul.naleul.domain.brainDump.support;

import com.naleul.naleul.domain.brainDump.client.BrainDumpAiRequest;
import com.naleul.naleul.domain.brainDump.client.BrainDumpAiResult;
import com.naleul.naleul.domain.brainDump.dto.request.BrainDumpParseRequest;
import com.naleul.naleul.domain.brainDump.dto.response.BrainDumpParseResponse;
import com.naleul.naleul.domain.brainDump.enums.DateType;
import com.naleul.naleul.domain.brainDump.enums.GoalMatchType;
import com.naleul.naleul.domain.task.enums.TaskKind;
import com.naleul.naleul.domain.task.enums.TaskPriority;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/**
 * AI 결과(또는 AI 실패 시 규칙 해석)를 서버 규칙으로 다듬어 2단계 응답을 만든다. 순수 계산.
 *
 * AI 를 믿지 않는 부분
 *  - 목표/세부 목표/마일스톤 id 는 후보 목록에 있는 것만 (없는 id 를 지어내면 연결 해제)
 *  - 날짜: 지난 날짜 → 오늘, 1년 넘게 먼 날짜 → 무시
 *  - 소요 시간 5~480분, 기본 30분 / 시각이 있으면 시각 차이로
 *  - 임시 목표: 실제로 쓰인 것만, 최대 3개, 이름 30자
 * 사용자가 칩으로 고른 날짜·시간은 AI 보다 우선한다.
 */
public final class BrainDumpResultSanitizer {

    static final int DEFAULT_MINUTES = 30;
    /** 사용자가 길이를 정하지 않은 블록의 최소 길이 — AI 가 "전화 15분"처럼 짧게 잡아도 30분 블록으로 (2026-10) */
    static final int MIN_UNSPECIFIED_MINUTES = 30;
    static final int MAX_TEMP_GOALS = 3;

    private BrainDumpResultSanitizer() {
    }

    public static BrainDumpParseResponse sanitize(BrainDumpParseRequest request, BrainDumpAiResult ai,
                                                  List<BrainDumpAiRequest.GoalContext> goals,
                                                  Map<Long, String> goalColors, LocalDate today) {
        Map<String, BrainDumpAiResult.Item> aiByKey = new HashMap<>();
        Map<String, BrainDumpAiResult.TempGoal> tempByKey = new LinkedHashMap<>();
        if (ai != null) {
            for (BrainDumpAiResult.Item i : ai.items()) if (i.clientKey() != null) aiByKey.putIfAbsent(i.clientKey(), i);
            for (BrainDumpAiResult.TempGoal t : ai.tempGoals()) {
                if (t.tempKey() != null && t.name() != null && !t.name().isBlank()) tempByKey.putIfAbsent(t.tempKey(), t);
            }
        }
        Map<Long, BrainDumpAiRequest.GoalContext> goalById = new HashMap<>();
        goals.forEach(g -> goalById.put(g.goalCategoryId(), g));

        List<BrainDumpParseResponse.Item> items = new ArrayList<>();
        Set<String> usedTemp = new LinkedHashSet<>();

        for (BrainDumpParseRequest.Item in : request.items()) {
            BrainDumpAiResult.Item a = aiByKey.get(in.clientKey());
            KoreanTaskTextParser.Parsed rule = KoreanTaskTextParser.parse(in.text(), today);

            String title = firstNonBlank(a != null ? a.title() : null, rule.title(), in.text());
            DateType type = a != null && a.dateType() != null ? a.dateType() : rule.dateType();
            LocalDate date = a != null && a.date() != null ? a.date() : rule.date();
            // AI 가 시각을 놓쳤으면("저녁 8시 정기 회의" → startTime 없음) 규칙 해석으로 채운다
            boolean aiHasTime = a != null && a.startTime() != null;
            LocalTime start = aiHasTime ? a.startTime() : rule.startTime();
            LocalTime end = aiHasTime ? a.endTime() : rule.endTime();
            // 소요 시간: 사용자가 글로 말했으면("2시간") 그대로, 아니면 AI 추정 — 단 정해진 게 없는 블록은 최소 30분 (2026-10)
            boolean userSaidMinutes = rule.minutes() != null;
            Integer minutes = userSaidMinutes ? rule.minutes()
                    : a != null && a.estimatedMinutes() != null ? Math.max(a.estimatedMinutes(), MIN_UNSPECIFIED_MINUTES) : null;
            TaskPriority priority = a != null && a.priority() != null ? a.priority() : TaskPriority.C;
            TaskKind kind = a != null && a.kind() != null ? a.kind() : rule.kind();

            // 사용자가 칩으로 고른 값 우선
            if (in.date() != null) {
                date = in.date();
                type = in.dateType() != null ? in.dateType() : DateType.ON;
            }
            if (in.startTime() != null) {
                start = in.startTime();
                end = in.endTime();
            }
            // 사용자가 정한 소요 시간이 AI 추정보다 우선. 종료 시각을 직접 고르지 않았으면 소요 시간으로 끝을 정한다
            if (in.estimatedMinutes() != null) {
                minutes = in.estimatedMinutes();   // 칩으로 직접 고른 길이는 30분 미만이어도 그대로
                if (in.endTime() == null) end = null;
            }

            // 날짜 정리
            if (date == null || date.isAfter(today.plusYears(1))) date = today;
            if (date.isBefore(today)) date = today;
            if (type == null || type == DateType.NONE) type = DateType.ON;
            if (start != null) type = DateType.ON; // 시각이 있으면 그날 하는 일

            // 시간 정리
            if (start == null) end = null;
            int estimated = clampMinutes(minutes);
            if (start != null && end != null) {
                long diff = Duration.between(start, end).toMinutes();
                if (diff <= 0) diff += 24 * 60; // 23:00~01:00
                if (diff > 0 && diff <= 24 * 60) estimated = (int) Math.min(diff, 24 * 60);
            }
            if (kind == null) kind = TaskKind.NORMAL;

            BrainDumpParseResponse.GoalLink link = link(a != null ? a.goal() : null, goalById, tempByKey, goalColors);
            if (link.type() == GoalMatchType.TEMP) usedTemp.add(link.tempKey());

            items.add(new BrainDumpParseResponse.Item(in.clientKey(), truncate(title, 100), type,
                    type == DateType.ON ? date : null, type == DateType.DUE ? date : null,
                    start, end, estimated, priority, kind, link));
        }

        // 쓰인 임시 목표만, 최대 3개 (넘치는 것은 연결 해제)
        List<BrainDumpParseResponse.TempGoal> temps = new ArrayList<>();
        Set<String> kept = new HashSet<>();
        for (String key : usedTemp) {
            if (temps.size() >= MAX_TEMP_GOALS) break;
            BrainDumpAiResult.TempGoal t = tempByKey.get(key);
            temps.add(new BrainDumpParseResponse.TempGoal(key, truncate(t.name().strip(), 30),
                    t.emoji() != null && !t.emoji().isBlank() ? truncate(t.emoji().strip(), 16) : "📌"));
            kept.add(key);
        }
        if (kept.size() < usedTemp.size()) {
            items.replaceAll(i -> i.goal().type() == GoalMatchType.TEMP && !kept.contains(i.goal().tempKey())
                    ? withGoal(i, BrainDumpParseResponse.GoalLink.none()) : i);
        }
        return new BrainDumpParseResponse(items, temps, ai == null);
    }

    private static BrainDumpParseResponse.GoalLink link(BrainDumpAiResult.Goal g,
                                                        Map<Long, BrainDumpAiRequest.GoalContext> goalById,
                                                        Map<String, BrainDumpAiResult.TempGoal> tempByKey,
                                                        Map<Long, String> goalColors) {
        if (g == null || g.type() == null) return BrainDumpParseResponse.GoalLink.none();
        if (g.type() == GoalMatchType.EXISTING) {
            BrainDumpAiRequest.GoalContext goal = g.goalCategoryId() != null ? goalById.get(g.goalCategoryId()) : null;
            if (goal == null) return BrainDumpParseResponse.GoalLink.none(); // 없는 id → 연결 해제
            BrainDumpAiRequest.SubGoal sub = goal.subGoals().stream()
                    .filter(s -> s.generalCategoryId().equals(g.generalCategoryId())).findFirst().orElse(null);
            BrainDumpAiRequest.MilestoneContext ms = goal.milestones().stream()
                    .filter(m -> m.milestoneId().equals(g.milestoneId())).findFirst().orElse(null);
            return new BrainDumpParseResponse.GoalLink(GoalMatchType.EXISTING,
                    goal.goalCategoryId(), goal.name(), goal.emoji(), goalColors.get(goal.goalCategoryId()), goal.temporary(),
                    sub != null ? sub.generalCategoryId() : null, sub != null ? sub.name() : null,
                    ms != null ? ms.milestoneId() : null, ms != null ? ms.title() : null,
                    null, truncate(g.reason(), 200));
        }
        if (g.type() == GoalMatchType.TEMP && g.tempKey() != null && tempByKey.containsKey(g.tempKey())) {
            BrainDumpAiResult.TempGoal t = tempByKey.get(g.tempKey());
            return new BrainDumpParseResponse.GoalLink(GoalMatchType.TEMP, null, t.name(), t.emoji(), null, true,
                    null, null, null, null, g.tempKey(), truncate(g.reason(), 200));
        }
        return BrainDumpParseResponse.GoalLink.none();
    }

    private static BrainDumpParseResponse.Item withGoal(BrainDumpParseResponse.Item i, BrainDumpParseResponse.GoalLink g) {
        return new BrainDumpParseResponse.Item(i.clientKey(), i.title(), i.dateType(), i.scheduledDate(), i.dueDate(),
                i.startTime(), i.endTime(), i.estimatedMinutes(), i.priority(), i.kind(), g);
    }

    static int clampMinutes(Integer m) {
        if (m == null || m <= 0) return DEFAULT_MINUTES;
        return Math.max(5, Math.min(m, 480));
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) if (v != null && !v.isBlank()) return v.strip();
        return "";
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
