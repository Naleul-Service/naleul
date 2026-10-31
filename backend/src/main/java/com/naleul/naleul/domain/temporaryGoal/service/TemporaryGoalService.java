package com.naleul.naleul.domain.temporaryGoal.service;

import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.generalCategory.repository.GeneralCategoryRepository;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus;
import com.naleul.naleul.domain.goalCategory.repository.GoalCategoryRepository;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.enums.TaskStatus;
import com.naleul.naleul.domain.temporaryGoal.repository.TemporaryGoalRepository;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 임시 목표 → "목표 구체화하기" (명세 3 · 3-2, BE-8).
 *  - 출발 목표 검증: 내 것 + 삭제 안 됨 + 임시 목표
 *  - AI 에 넘길 "이미 있는 할 일" 문맥 (같은 일을 계획에 다시 만들지 않도록)
 *
 * 2026-10: AI 없이 직접 만든 예전 목표도 같은 흐름으로 "AI 로 다시 설계"할 수 있다.
 *  임시 목표와 달리 새 목표로 옮기지 않고, 확정하면 그 목표 자체에 계획을 채운다 (GoalConfirmService).
 */
@Component
@RequiredArgsConstructor
public class TemporaryGoalService {

    static final int MAX_CONTEXT_TASKS = 30;

    private final GoalCategoryRepository goalCategoryRepository;
    private final TemporaryGoalRepository temporaryGoalRepository;

    /** 예전 목표의 세부 목표 이름 (기존 테스트 생성자 호환을 위해 필드 주입) */
    @Autowired(required = false)
    private GeneralCategoryRepository generalCategoryRepository;

    /** 예전 목표의 지금 하고 있는 루틴 (초안에 자동으로 들어가므로 AI 에게 알려 준다) */
    @Autowired(required = false)
    private com.naleul.naleul.domain.goalCreation.service.LegacyPlanMerger legacyPlanMerger;

    public GoalCategory findOwnedTemporary(Long userId, Long goalCategoryId) {
        return goalCategoryRepository.findById(goalCategoryId)
                .filter(g -> g.getUser().getUserId().equals(userId))
                .filter(g -> g.getGoalCategoryStatus() != GoalCategoryStatus.DELETED)
                .filter(GoalCategory::isTemporary)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_SOURCE_GOAL));
    }

    /**
     * AI 로 설계할 출발 목표: 내 임시 목표 또는 AI 없이 만든 예전 목표 (2026-10).
     * 그 밖(완료 · 삭제 · 업무형 · 습관 · 이미 AI 목표)은 400 INVALID_SOURCE_GOAL
     */
    public GoalCategory findOwnedSource(Long userId, Long goalCategoryId) {
        return goalCategoryRepository.findById(goalCategoryId)
                .filter(g -> g.getUser().getUserId().equals(userId))
                .filter(g -> g.getGoalCategoryStatus() != GoalCategoryStatus.DELETED)
                .filter(g -> g.isTemporary() || g.isLegacyManual())
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_SOURCE_GOAL));
    }

    /** AI 프롬프트의 [기존 목표 정보] / [기존 목표와 할 일] 에 들어갈 문장. 임시 · 예전 목표가 아니면 null */
    public String buildContext(GoalCategory source) {
        if (source == null) return null;
        List<Task> tasks = temporaryGoalRepository.findTasksOfGoal(source.getGoalCategoryId());
        if (source.isTemporary()) return format(source.getEmoji(), source.getGoalCategoryName(), tasks);
        if (!source.isLegacyManual()) return null;
        List<String> subGoals = generalCategoryRepository == null ? List.of()
                : generalCategoryRepository.findAllActiveByGoalCategoryId(source.getGoalCategoryId()).stream()
                        .filter(g -> !g.isDefaultBucket())
                        .map(GeneralCategory::getGeneralCategoryName)
                        .toList();
        List<String> routines = legacyPlanMerger == null ? List.of()
                : legacyPlanMerger.activeRoutines(source).stream().map(TemporaryGoalService::describeRoutine).toList();
        return formatLegacy(source.getEmoji(), source.getGoalCategoryName(), source.getMotive(), subGoals, routines, tasks);
    }

    /**
     * 예전 목표 문맥. 루틴이 만든 Task 는 같은 이름이 날마다 반복되므로 이름별로 묶어 횟수만 보여준다.
     */
    static String formatLegacy(String emoji, String name, String motive, List<String> subGoals, List<Task> tasks) {
        return formatLegacy(emoji, name, motive, subGoals, List.of(), tasks);
    }

    static String describeRoutine(com.naleul.naleul.domain.routine.entity.Routine r) {
        String days = r.getRepeatDays().stream()
                .map(d -> d.getDayOfWeek())
                .filter(java.util.Objects::nonNull)
                .sorted()
                .map(d -> d.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.KOREAN))
                .collect(java.util.stream.Collectors.joining("·"));
        Integer minutes = com.naleul.naleul.domain.goalCreation.service.LegacyPlanMerger.durationOf(r);
        return r.getRoutineName() + " (" + (days.isEmpty() ? "요일 없음" : days) + ", " + minutes + "분)";
    }

    /**
     * @param routines 지금 하고 있는 루틴 설명 ("영어 스피킹 (월·수·금, 30분)") — 초안에 자동으로 들어간다
     */
    static String formatLegacy(String emoji, String name, String motive, List<String> subGoals, List<String> routines,
                               List<Task> tasks) {
        StringBuilder sb = new StringBuilder();
        sb.append("사용자가 예전에 AI 없이 직접 만든 목표 \"").append(emoji != null ? emoji + " " : "").append(name)
                .append("\"를 AI 로 다시 설계하는 중이에요. 확정하면 이 목표에 계획이 더해지고, 지금 있는 것은 그대로 남아요.\n");
        if (motive != null && !motive.isBlank()) sb.append("사용자가 적어 둔 동기: ").append(motive.strip()).append("\n");
        // 2026-10 AI 비용: 이미 아는 것은 묻지 않게 — 질문 한 번이 AI 호출 한 번
        sb.append("이미 아는 내용(목표 이름 · 동기 · 하고 있는 루틴)은 다시 묻지 말고, 계획에 꼭 필요한 것(목표 수치 · 기한)만 짧게 물어봐.\n");
        if (!subGoals.isEmpty()) {
            sb.append("이미 있는 세부 목표: ").append(String.join(", ", subGoals.stream().limit(10).toList())).append("\n");
        }
        if (!routines.isEmpty()) {
            sb.append("지금 하고 있는 루틴 (계획에 그대로 들어가요 — 다시 만들지 마세요. 주간 시간 예산에서 이 시간을 먼저 빼고 세워 주세요):\n");
            routines.forEach(r -> sb.append("- ").append(r).append("\n"));
        }
        if (tasks.isEmpty()) {
            sb.append("아직 이 목표에 할 일은 없어요.\n");
        } else {
            Map<String, int[]> byName = new LinkedHashMap<>();   // 이름 → [전체, 완료]
            tasks.forEach(t -> {
                int[] c = byName.computeIfAbsent(t.getTaskName(), k -> new int[2]);
                c[0]++;
                if (t.getTaskStatus() == TaskStatus.COMPLETED) c[1]++;
            });
            sb.append("이 목표에서 해 온 할 일:\n");
            byName.entrySet().stream().limit(MAX_CONTEXT_TASKS).forEach(e -> sb.append("- ").append(e.getKey())
                    .append(" (").append(e.getValue()[0]).append("회 중 ").append(e.getValue()[1]).append("회 완료)\n"));
            if (byName.size() > MAX_CONTEXT_TASKS) sb.append("- 외 ").append(byName.size() - MAX_CONTEXT_TASKS).append("개\n");
            sb.append("이 할 일 · 세부 목표는 그대로 남아요. 같은 일을 계획에 다시 만들지 말고, ")
                    .append("해 온 일을 바탕으로 수치 목표 · 마일스톤 · 앞으로의 루틴을 세워 주세요.\n");
        }
        return sb.toString();
    }

    static String format(String emoji, String name, List<Task> tasks) {
        StringBuilder sb = new StringBuilder();
        sb.append("임시 목표 \"").append(emoji != null ? emoji + " " : "").append(name).append("\"를 구체화하는 중이에요.\n");
        if (tasks.isEmpty()) {
            sb.append("아직 이 목표에 할 일은 없어요.\n");
        } else {
            sb.append("이 목표에 이미 있는 할 일:\n");
            tasks.stream().limit(MAX_CONTEXT_TASKS).forEach(t -> sb.append("- ").append(t.getTaskName())
                    .append(" (").append(describe(t)).append(")\n"));
            if (tasks.size() > MAX_CONTEXT_TASKS) sb.append("- 외 ").append(tasks.size() - MAX_CONTEXT_TASKS).append("개\n");
            sb.append("이 할 일들은 목표를 확정하면 새 목표로 자동으로 옮겨져요. ")
                    .append("계획에 같은 할 일을 다시 만들지 말고, 이미 있는 일을 고려해 나머지 계획을 세워 주세요.\n");
        }
        return sb.toString();
    }

    private static String describe(Task t) {
        String status = t.getTaskStatus() == TaskStatus.COMPLETED ? "완료" : "미완료";
        LocalDate due = t.getDueDate();
        LocalDate date = t.getPlannedStartAt() != null ? t.getPlannedStartAt().toLocalDate() : t.getRoutineDate();
        if (due != null) return "마감 " + due.getMonthValue() + "/" + due.getDayOfMonth() + ", " + status;
        if (date != null) return date.getMonthValue() + "/" + date.getDayOfMonth() + ", " + status;
        return status;
    }
}
