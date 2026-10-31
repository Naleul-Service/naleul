package com.naleul.naleul.domain.goalCreation.client;

import com.naleul.naleul.domain.generalCategory.enums.MetricComparator;
import com.naleul.naleul.domain.generalCategory.enums.MetricPeriod;
import com.naleul.naleul.domain.goalCategory.enums.GoalTopic;
import com.naleul.naleul.domain.goalCategory.kind.GoalSubType;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto.*;
import com.naleul.naleul.domain.goalCreation.dto.plan.TaskType;
import com.naleul.naleul.domain.goalCreation.dto.plan.Weekday;
import com.naleul.naleul.domain.goalCreation.dto.slot.*;
import com.naleul.naleul.domain.goalCreation.enums.MessageRole;
import com.naleul.naleul.domain.user.enums.PlanningStyle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI를 실제로 호출하지 않는 가짜 구현 (goal.ai.client=fake 일 때만 등록).
 *
 * ⚠️ matchIfMissing을 일부러 켜지 않음: 설정이 빠진 운영 서버에서 가짜 계획이 나가는 사고를 막기 위해.
 *    설정이 없으면 ClaudeGoalAiClient(BE-7)가 기본으로 등록된다.
 *
 * 왜 필요한가
 *  - 로컬/테스트에서 API 키 없이, 비용 없이, 항상 같은 결과로 BE-3~BE-6을 개발·테스트하기 위해
 *  - 실제 AI는 응답이 매번 달라서 "5문항 강제" 같은 서버 규칙을 테스트하기 어렵다
 *
 * 인터뷰 규칙 (단순)
 *  - 마지막 사용자 답변을 "비어 있는 첫 슬롯"의 값으로 넣는다
 *  - 다음 비어 있는 슬롯을 질문한다. 남은 슬롯이 없으면 READY
 *  - 답변에 "[reject]"가 있으면 REJECT, "[ask-forever]"가 있으면 슬롯을 안 채우고 계속 ASK (5문항 강제 테스트용)
 *
 * 계획 규칙
 *  - 슬롯으로 기간·수치를 정하고, 명세 4-5 HARD 규칙을 통과하는 계획을 만든다
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "goal.ai", name = "client", havingValue = "fake")
public class FakeGoalAiClient implements GoalAiClient {

    private static final String MODEL = "fake";
    private static final Pattern NUMBER = Pattern.compile("(\\d+(?:\\.\\d+)?)");
    private static final Pattern MONTHS = Pattern.compile("(\\d+)\\s*개월");

    private static final Map<SlotKey, String> QUESTIONS = Map.of(
            SlotKey.GOAL_STATEMENT, "어떤 목표를 이루고 싶으세요?",
            SlotKey.METRIC, "지금 수치와 목표 수치를 알려줄 수 있을까요?",
            SlotKey.MOTIVATION, "왜 이 목표를 이루고 싶은지 조금 더 구체적으로 알려줄 수 있을까요?",
            SlotKey.DEADLINE, "언제까지 이루고 싶으세요?",
            SlotKey.APPROACH, "어떻게 이루고 싶으세요? 이미 정해 둔 계획이나 꼭 할 일이 있으면 알려주세요.",
            SlotKey.PRACTICE_PREFERENCE, "실천하기 좋은 요일·시간대가 있나요? 평소에 미리미리 하는 편인지, 몰아서 하는 편인지도 알려주세요."
    );

    private static final Map<SlotKey, List<String>> QUICK_REPLIES = Map.of(
            SlotKey.GOAL_STATEMENT, List.of("살 빼기", "자격증 따기", "운동 습관 만들기"),
            SlotKey.METRIC, List.of("78 → 68kg", "정확히 몰라요"),
            SlotKey.MOTIVATION, List.of("건강 때문에", "자신감을 위해", "특별한 일정이 있어서"),
            SlotKey.DEADLINE, List.of("3개월", "6개월", "올해 안에", "잘 모르겠어요"),
            SlotKey.APPROACH, List.of("주 3회 헬스", "2주 이론 후 기출", "아직 없어요"),
            SlotKey.PRACTICE_PREFERENCE, List.of("평일 저녁, 미리미리", "주말에 몰아서", "그때그때")
    );

    // ───────────────────────── 인터뷰 ─────────────────────────

    @Override
    public InterviewTurnResult interviewTurn(InterviewTurnRequest request) {
        long started = System.currentTimeMillis();
        GoalSlots slots = request.slots() != null ? request.slots() : GoalSlots.empty();
        String answer = lastUserAnswer(request.history());

        if (answer != null && answer.contains("[reject]")) {
            return new InterviewTurnResult(ExtractedSlots.none(), PlanningStyleSignal.unknown(),
                    NextAction.REJECT, null, null, List.of(), meta(started));
        }

        boolean askForever = answer != null && answer.contains("[ask-forever]");
        ExtractedSlots extracted = ExtractedSlots.none();
        PlanningStyleSignal signal = PlanningStyleSignal.unknown();
        GoalSlots afterMerge = slots;

        if (answer != null && !askForever) {
            SlotKey target = nextToAsk(slots);
            if (target != null) {
                extracted = extract(target, answer, request.today());
                afterMerge = applyForPreview(slots, target, extracted);
            }
            signal = detectStyle(answer);
        }

        SlotKey next = askForever ? SlotKey.MOTIVATION : nextToAsk(afterMerge);
        if (next == null || request.remainingQuestions() <= 0) {
            return new InterviewTurnResult(extracted, signal, NextAction.READY, null, null, List.of(), meta(started));
        }

        return new InterviewTurnResult(extracted, signal, NextAction.ASK, next,
                QUESTIONS.get(next), QUICK_REPLIES.get(next), meta(started));
    }

    /** V52: 이유(motivation)는 묻지 않는다 — 비어 있는 첫 슬롯 중 motivation 을 뺀 것 */
    private static SlotKey nextToAsk(GoalSlots slots) {
        for (SlotKey k : SlotKey.values()) {
            if (k != SlotKey.MOTIVATION && slots.get(k).isEmpty()) return k;
        }
        return null;
    }

    private String lastUserAnswer(List<ConversationMessage> history) {
        if (history == null) return null;
        for (int i = history.size() - 1; i >= 0; i--) {
            ConversationMessage m = history.get(i);
            if (m.role() == MessageRole.USER) {
                return m.skip() ? null : m.content();
            }
        }
        return null;
    }

    private ExtractedSlots extract(SlotKey target, String answer, LocalDate today) {
        return switch (target) {
            case GOAL_STATEMENT -> new ExtractedSlots(answer, null, null, null, null, List.of());
            case METRIC -> {
                MetricSlot metric = parseMetric(answer);
                yield metric != null
                        ? new ExtractedSlots(null, metric, null, null, null, List.of())
                        : new ExtractedSlots(null, null, null, null, null, List.of(SlotKey.METRIC));
            }
            case MOTIVATION -> new ExtractedSlots(null, null, answer, null, null, List.of());
            case APPROACH -> new ExtractedSlots(null, null, null, null, null, List.of(), answer);
            case DEADLINE -> new ExtractedSlots(null, null, null, parseDeadline(answer, today), null, List.of());
            case PRACTICE_PREFERENCE -> new ExtractedSlots(null, null, null, null,
                    new PracticePreferenceSlot(null, null, null, answer), List.of());
        };
    }

    /** "78인데 68까지" → current 78, target 68. 숫자 2개 미만이면 수치 없는 목표로 보고 SKIPPED */
    private MetricSlot parseMetric(String answer) {
        Matcher m = NUMBER.matcher(answer);
        List<BigDecimal> numbers = new ArrayList<>();
        while (m.find()) numbers.add(new BigDecimal(m.group(1)));
        if (numbers.size() < 2) return null;
        String unit = answer.contains("kg") || answer.contains("키로") ? "kg" : "";
        return new MetricSlot(unit.equals("kg") ? "체중" : "수치", numbers.get(0), numbers.get(1), unit);
    }

    /** "3개월" → 오늘+3개월. 못 알아들으면 6개월 + isFlexible=true */
    private DeadlineSlot parseDeadline(String answer, LocalDate today) {
        Matcher m = MONTHS.matcher(answer);
        if (m.find()) {
            return new DeadlineSlot(today.plusMonths(Integer.parseInt(m.group(1))), false);
        }
        return new DeadlineSlot(today.plusMonths(6), true);
    }

    private PlanningStyleSignal detectStyle(String answer) {
        if (answer.contains("몰아") || answer.contains("그때그때") || answer.contains("시간 날 때")) {
            return new PlanningStyleSignal(PlanningStyleGuess.SPONTANEOUS, answer);
        }
        if (answer.contains("미리") || answer.contains("매일") || answer.contains("계획")) {
            return new PlanningStyleSignal(PlanningStyleGuess.PLANNER, answer);
        }
        return PlanningStyleSignal.unknown();
    }

    /** 다음 질문을 고르기 위해 "병합했다면" 어떤 모양인지만 계산 (실제 병합은 서비스가 함) */
    private GoalSlots applyForPreview(GoalSlots slots, SlotKey target, ExtractedSlots e) {
        if (e.skippedSlots().contains(target)) return slots.skip(target);
        return switch (target) {
            case GOAL_STATEMENT -> slots.withGoalStatement(Slot.filled(e.goalStatement()));
            case METRIC -> slots.withMetric(Slot.filled(e.metric()));
            case MOTIVATION -> slots.withMotivation(Slot.filled(e.motivation()));
            case DEADLINE -> slots.withDeadline(Slot.filled(e.deadline()));
            case PRACTICE_PREFERENCE -> slots.withPracticePreference(Slot.filled(e.practicePreference()));
            case APPROACH -> slots.withApproach(Slot.filled(e.approach()));
        };
    }

    // ───────────────────────── 계획 ─────────────────────────

    @Override
    public PlanGenerationResult generatePlan(PlanGenerationRequest request) {
        long started = System.currentTimeMillis();
        GoalSlots slots = request.slots();
        LocalDate today = request.today();
        PlanningStyle style = request.planningStyle() != null ? request.planningStyle() : PlanningStyle.PLANNER;

        LocalDate start = today;
        LocalDate end = slots.deadline().isFilled() && slots.deadline().value().endDate() != null
                ? slots.deadline().value().endDate()
                : today.plusMonths(6);
        if (!end.isAfter(start.plusDays(7))) {
            end = start.plusMonths(1);   // 너무 짧은 기간은 1개월로 보정 (마일스톤 4개를 나눌 수 있게)
        }

        MetricSlot metric = slots.metric().isFilled() ? slots.metric().value() : null;
        String title = slots.hasGoalStatement() ? truncate(slots.goalStatement().value(), 40) : "나의 목표";

        PlanGoal goal = new PlanGoal(
                title, "🎯", GoalTopic.ETC,
                slots.motivation().isFilled() ? truncate(slots.motivation().value(), 500) : null,
                metric != null ? new PlanGoalMetric(metric.name(), metric.unit(), metric.currentValue(), metric.targetValue()) : null,
                start, end, style,
                "[FAKE] 로컬 테스트용으로 만든 계획이에요.",
                request.kind() != null ? request.kind().subType() : GoalSubType.OTHER
        );

        List<PlanSubGoal> subGoals = List.of(
                new PlanSubGoal("sg1", "준비와 기록", "📝", "진행 상황을 매일 기록해요",
                        new PlanSubGoalMetric("기록 횟수", "회", MetricComparator.GTE, BigDecimal.valueOf(5), MetricPeriod.WEEKLY)),
                new PlanSubGoal("sg2", "핵심 실천", "🏃", "목표를 위한 핵심 행동을 꾸준히 해요",
                        new PlanSubGoalMetric("실천 횟수", "회", MetricComparator.GTE, BigDecimal.valueOf(3), MetricPeriod.WEEKLY))
        );

        List<PlanMilestone> milestones = buildMilestones(start, end, metric);
        List<PlanTask> tasks = buildTasks(start, milestones, style);

        GoalPlanDto plan = new GoalPlanDto(goal, subGoals, milestones, tasks, List.of());
        log.info("[FakeGoalAiClient] 계획 생성 완료 style={}, milestones={}, tasks={}", style, milestones.size(), tasks.size());
        return new PlanGenerationResult(plan, meta(started));
    }

    /** 기간을 4등분. 마지막 = 목표 종료일, 수치는 시작→목표로 선형 변화 */
    private List<PlanMilestone> buildMilestones(LocalDate start, LocalDate end, MetricSlot metric) {
        long totalDays = ChronoUnit.DAYS.between(start, end);
        List<PlanMilestone> result = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            LocalDate due = i == 4 ? end : start.plusDays(totalDays * i / 4);
            BigDecimal target = null;
            if (metric != null && metric.currentValue() != null && metric.targetValue() != null) {
                BigDecimal diff = metric.targetValue().subtract(metric.currentValue());
                target = i == 4
                        ? metric.targetValue()
                        : metric.currentValue().add(diff.multiply(BigDecimal.valueOf(i))
                                .divide(BigDecimal.valueOf(4), 2, RoundingMode.HALF_UP));
            }
            result.add(new PlanMilestone("ms" + i, i + "단계", i + "번째 구간 목표", due, target));
        }
        return result;
    }

    /**
     * 루틴 2개(주 35분 + 60분 = 95분) + 마일스톤마다 일회성 1개.
     * PLANNER: 구간 마감 3일 전에 끝내도록, SPONTANEOUS: 구간 마지막 날 직전에 몰아서
     */
    private List<PlanTask> buildTasks(LocalDate start, List<PlanMilestone> milestones, PlanningStyle style) {
        List<PlanTask> tasks = new ArrayList<>();
        tasks.add(new PlanTask("t1", TaskType.ROUTINE, "sg1", null, "진행 상황 5분 기록하기", "✍️", 5,
                List.of(Weekday.values()), LocalTime.of(21, 0), start, null, null, null,
                "오늘 한 일과 수치를 한 줄로 적어요."));
        tasks.add(new PlanTask("t2", TaskType.ROUTINE, "sg2", null, "핵심 실천 20분", "🏃", 20,
                List.of(Weekday.MON, Weekday.WED, Weekday.FRI), null, start, null, null, null,
                "목표에 가장 효과가 큰 행동 하나를 20분 동안 해요."));

        LocalDate prevDue = start;
        int n = 3;
        for (PlanMilestone ms : milestones) {
            LocalDate due = style == PlanningStyle.SPONTANEOUS ? ms.dueDate() : ms.dueDate().minusDays(3);
            LocalDate scheduled = style == PlanningStyle.SPONTANEOUS ? ms.dueDate().minusDays(1) : due.minusDays(2);
            // 구간이 짧아도 "오늘 이후, 이전 마일스톤 이후"를 지키도록 보정
            if (scheduled.isBefore(prevDue)) scheduled = prevDue;
            if (due.isBefore(scheduled)) due = scheduled;
            if (due.isAfter(ms.dueDate())) due = ms.dueDate();
            tasks.add(new PlanTask("t" + n++, TaskType.ONE_TIME, "sg1", ms.tempId(),
                    ms.title() + " 점검하기", "📏", 20, null, null, null, null, scheduled, due));
            prevDue = ms.dueDate();
        }
        return tasks;
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    /**
     * 로컬 개발용 "말로 고치기": "종료일 N주 늦춰" 만 규칙으로 처리하고, 나머지는 변경 없이 안내만 한다.
     * (화면 흐름 확인용 — 실제 해석은 Claude 클라이언트)
     */
    @Override
    public GoalEditAiResult suggestGoalEdit(GoalEditAiRequest request) {
        long started = System.currentTimeMillis();
        Matcher m = Pattern.compile("(\\d+)\\s*주\\s*(늦|미루|연장)").matcher(request.instruction());
        Matcher end = Pattern.compile("\"endDate\"\\s*:\\s*\"(\\d{4}-\\d{2}-\\d{2})\"").matcher(request.goalContext());
        if (m.find() && end.find()) {
            LocalDate newEnd = LocalDate.parse(end.group(1)).plusWeeks(Integer.parseInt(m.group(1)));
            var op = new com.naleul.naleul.domain.goalEdit.dto.GoalEditOp(
                    com.naleul.naleul.domain.goalEdit.dto.GoalEditOpType.UPDATE_GOAL,
                    null, null, null, null, null, null, newEnd, null, null, null);
            return new GoalEditAiResult("종료일을 " + newEnd + "로 늦출게요.", List.of(op), meta(started));
        }
        return new GoalEditAiResult("로컬 개발용 AI 라서 이 요청은 바꿀 수 없어요.", List.of(), meta(started));
    }

    /**
     * 로컬 개발용: "겹치는 루틴 정리" → overlaps 의 첫 묶음에서 두 번째 루틴을 1시간 30분 뒤로.
     * 그 밖의 요청은 바꿀 수 없다고 답해요.
     */
    @Override
    public ScheduleEditAiResult suggestScheduleEdit(ScheduleEditAiRequest request) {
        long started = System.currentTimeMillis();
        Matcher ov = Pattern.compile("\"routineIds\"\\s*:\\s*\\[\\s*(\\d+)\\s*,\\s*(\\d+)").matcher(request.context());
        if (request.instruction().contains("겹") && ov.find()) {
            long id = Long.parseLong(ov.group(2));
            Matcher st = Pattern.compile("\"id\"\\s*:\\s*" + id + "\\s*,[^}]*?\"start\"\\s*:\\s*\"(\\d{2}):(\\d{2})\"").matcher(request.context());
            if (st.find()) {
                java.time.LocalTime t = java.time.LocalTime.of(Integer.parseInt(st.group(1)), Integer.parseInt(st.group(2))).plusMinutes(90);
                var op = new com.naleul.naleul.domain.scheduleEdit.dto.ScheduleEditOp(
                        com.naleul.naleul.domain.scheduleEdit.dto.ScheduleEditOpType.MOVE_ROUTINE, id, null, t, null, null);
                return new ScheduleEditAiResult("겹치던 루틴 하나를 " + t + "로 옮길게요.", List.of(op), meta(started));
            }
        }
        return new ScheduleEditAiResult("로컬 개발용 AI 라서 이 요청은 바꿀 수 없어요.", List.of(), meta(started));
    }

    private AiCallMeta meta(long started) {
        return new AiCallMeta(MODEL, 0, 0, System.currentTimeMillis() - started);
    }
}
