package com.naleul.naleul.domain.admin.service;

import com.naleul.naleul.domain.admin.dto.AdminDashboardResponse.AiQuality;
import com.naleul.naleul.domain.admin.dto.AdminDashboardResponse.AiQualityRow;
import com.naleul.naleul.domain.admin.dto.AdminDashboardResponse.OutcomeCheck;
import com.naleul.naleul.domain.admin.dto.AdminDashboardResponse.OutcomeReasonRow;
import com.naleul.naleul.domain.admin.repository.AiQualityQueryRepository;
import com.naleul.naleul.domain.admin.repository.AiQualityQueryRepository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.naleul.naleul.domain.admin.service.AdminDashboardService.percent;

/**
 * 운영 대시보드 "AI 품질" 섹션 계산.
 * 지표 정의는 노션 Tracking Plan 「AI 품질 지표」와 같다 — 숫자를 바꾸면 노션도 같이 고친다.
 */
@Component
@RequiredArgsConstructor
public class AiQualityCalculator {

    private final AiQualityQueryRepository query;

    /**
     * @param fromAt / toAt   서버 시간대 기간 (created_at 비교)
     * @param kstFrom / kstTo 한국 시간 기간 (한국 시간으로 저장한 컬럼 비교)
     */
    public AiQuality calculate(LocalDate from, LocalDate today, LocalDateTime fromAt, LocalDateTime toAt,
                               LocalDateTime kstFrom, LocalDateTime kstTo) {
        List<AiQualityRow> rows = new ArrayList<>();

        // 1. AI 목표 생성 — V68 AI 초안 정확도와 같은 정의 (자세히는 AI 초안 탭).
        //    배포 전 기간처럼 V68 기록이 없으면 예전 방식(첫 초안 · Task 수정 · 달성률)으로 보여준다.
        DraftReviewRow d = query.draftReview(fromAt, toAt);
        if (d.sessions() > 0) {
            rows.add(new AiQualityRow("GOAL_PLAN", "AI 목표 생성",
                    "다시 생성 없이 확정 (성향·강도 변경 제외)", percent(d.noRegen(), d.sessions()), d.sessions(),
                    "고친 칸 비율 (100% − 초안 보존율)", percent(d.aiFields() - d.keptFields(), d.aiFields()), d.aiFields(),
                    "첫 행동 후 14일 루틴 실천률", percent(d.routineDone(), d.routinePlanned()), d.routinePlanned(), null,
                    "루틴 시간 변화 (확정 ÷ 초안)", percent(d.confirmedMinutes(), d.draftMinutes()), d.weeklySessions()));
        } else {
            GoalPlanRow g = query.goalPlan(fromAt, toAt);
            rows.add(new AiQualityRow("GOAL_PLAN", "AI 목표 생성",
                    "첫 초안을 그대로 확정 · 예전 방식", percent(g.firstDraft(), g.confirmed()), g.confirmed(),
                    "AI 가 만든 Task 중 고친 비율 · 예전 방식", percent(g.editedTasks(), g.draftTasks()), g.draftTasks(),
                    "AI 목표 달성률 · 예전 방식", percent(g.aiAchieved(), g.aiEnded()), g.aiEnded(), null,
                    "루틴 시간 변화 (확정 ÷ 초안) · n 은 분", percent(g.confirmedMinutes(), g.draftMinutes()), g.draftMinutes()));
        }

        // 2. 자동 배치 (어제까지 — 오늘 배치는 아직 결과가 없다)
        PlacementRow p = query.placement(from, today.minusDays(1));
        rows.add(new AiQualityRow("TIMETABLE", "TimeTable 자동 배치",
                "배치한 시간 그대로 둠", percent(p.kept(), p.placed()), p.placed(),
                "사용자가 옮긴 비율", percent(p.placed() - p.kept(), p.placed()), p.placed(),
                "배치한 시간 그대로 완료", percent(p.keptDone(), p.placed()), p.placed(), null,
                "배치 실패율 (빈 시간 없음)", percent(p.planUnplaced(), p.planPlaced() + p.planUnplaced()),
                p.planPlaced() + p.planUnplaced()));

        // 3~5. 말로 고치기 · AI 일정 조정 · 브레인 덤프
        Map<String, SuggestionRow> sug = query.suggestions(fromAt, toAt).stream()
                .collect(Collectors.toMap(SuggestionRow::feature, Function.identity()));
        Map<String, DoneRow> done = query.suggestionTaskResults(fromAt, toAt, today).stream()
                .collect(Collectors.toMap(DoneRow::feature, Function.identity()));

        SuggestionRow ge = sug.getOrDefault("GOAL_EDIT", empty("GOAL_EDIT"));
        BeforeAfterRow geBa = query.goalEditBeforeAfter(fromAt, toAt, today);
        rows.add(editRow("GOAL_EDIT", "말로 고치기 (목표 수정)", ge,
                "수정 후 14일 목표 실천률", percent(geBa.afterDone(), geBa.afterDue()), geBa.afterDue(),
                percent(geBa.beforeDone(), geBa.beforeDue())));

        SuggestionRow se = sug.getOrDefault("SCHEDULE_EDIT", empty("SCHEDULE_EDIT"));
        DoneRow seTask = done.getOrDefault("SCHEDULE_EDIT", new DoneRow("SCHEDULE_EDIT", 0, 0));
        DoneRow seRoutine = query.movedRoutineResults(fromAt, toAt, today);
        long seDue = seTask.due() + seRoutine.due();
        rows.add(editRow("SCHEDULE_EDIT", "AI 일정 조정", se,
                "옮긴 일정 완료율", percent(seTask.done() + seRoutine.done(), seDue), seDue, null));

        SuggestionRow bd = sug.getOrDefault("BRAIN_DUMP", empty("BRAIN_DUMP"));
        DoneRow bdDone = done.getOrDefault("BRAIN_DUMP", new DoneRow("BRAIN_DUMP", 0, 0));
        rows.add(new AiQualityRow("BRAIN_DUMP", "브레인 덤프",
                "AI 정리 그대로 확정", percent(bd.appliedUnedited(), bd.applied()), bd.applied(),
                "고친 항목 비율", percent(bd.edited(), bd.appliedItems()), bd.appliedItems(),
                "브레인 덤프 Task 완료율", percent(bdDone.done(), bdDone.due()), bdDone.due(), null,
                "연결 목표를 바꾼 비율", percent(bd.goalChanged(), bd.appliedItems()), bd.appliedItems()));

        // 6. 나의 패턴 제안
        PatternRow pa = query.patternActions(kstFrom, kstTo);
        BeforeAfterRow paBa = query.patternBeforeAfter(kstFrom, kstTo, today);
        long paTotal = pa.applied() + pa.dismissed();
        rows.add(new AiQualityRow("PATTERN", "나의 패턴 제안",
                "제안을 반영", percent(pa.applied(), paTotal), paTotal,
                null, null, 0,
                "반영 후 28일 루틴 실천률", percent(paBa.afterDone(), paBa.afterDue()), paBa.afterDue(),
                percent(paBa.beforeDone(), paBa.beforeDue()),
                null, null, 0));

        return new AiQuality(rows, outcomeCheck(query.outcomeChecks(fromAt, toAt)));
    }

    /** 말로 고치기 · 일정 조정 — 미리보기 → 적용 흐름이 같다 */
    private static AiQualityRow editRow(String key, String label, SuggestionRow s,
                                        String resultLabel, Integer resultRate, long resultBase, Integer beforeRate) {
        return new AiQualityRow(key, label,
                "제안을 적용", percent(s.applied(), s.withValid()), s.withValid(),
                "제안 중 빼고 적용한 비율", percent(s.appliedValid() - s.appliedItems(), s.appliedValid()), s.appliedValid(),
                resultLabel, resultRate, resultBase, beforeRate,
                "엉뚱한 제안 비율 (서버가 거름)", percent(s.suggested() - s.valid(), s.suggested()), s.suggested());
    }

    private static SuggestionRow empty(String feature) {
        return new SuggestionRow(feature, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    static OutcomeCheck outcomeCheck(List<OutcomeCheckRow> rows) {
        long judged = 0, answered = 0, dismissed = 0, pending = 0, compared = 0, matched = 0, metric80 = 0;
        List<OutcomeReasonRow> byReason = new ArrayList<>();
        for (OutcomeCheckRow r : rows) {
            judged += r.autoJudged();
            answered += r.answered();
            dismissed += r.dismissed();
            pending += r.pending();
            compared += r.compared();
            matched += r.matched();
            metric80 += r.metric80();
            if (r.compared() > 0) {
                byReason.add(new OutcomeReasonRow(r.reason(), reasonLabel(r.reason()), r.compared(), r.matched(),
                        percent(r.matched(), r.compared())));
            }
        }
        return new OutcomeCheck(judged, answered, dismissed, pending, percent(answered, judged),
                compared, matched, percent(matched, compared), metric80, byReason);
    }

    static String reasonLabel(String reason) {
        return switch (reason) {
            case "METRIC" -> "수치 (80% 이상 달성)";
            case "ROUTINE_RATE" -> "루틴 실천률";
            case "TASK_RATE" -> "할 일 실천률";
            case "NO_RECORD" -> "기록 없음";
            default -> "판정 근거 없음 (예전 기록)";
        };
    }
}
