package com.naleul.naleul.domain.goalCreation.personal;

import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto.PlanTask;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI 초안 → 확정한 계획 사이에 사용자가 고친 Task 수 (tempId 로 짝을 맞춘다).
 * 추가 · 삭제 · 이름 · 요일 · 소요 시간 · 시작 시각 중 하나라도 바뀌면 1개로 센다.
 */
public final class PlanEditCounter {

    private PlanEditCounter() {
    }

    public static int count(GoalPlanDto draft, GoalPlanDto confirmed) {
        Map<String, PlanTask> before = byTempId(draft.tasks());
        Map<String, PlanTask> after = byTempId(confirmed.tasks());
        int edited = 0;
        for (String id : before.keySet()) {
            PlanTask a = before.get(id), b = after.get(id);
            if (b == null || changed(a, b)) edited++;
        }
        for (String id : after.keySet()) {
            if (!before.containsKey(id)) edited++;
        }
        // tempId 가 없는 Task(예전 앱이 새로 만든 것)는 추가로 센다
        edited += (int) confirmed.tasks().stream().filter(t -> t.tempId() == null).count();
        return edited;
    }

    static boolean changed(PlanTask a, PlanTask b) {
        return !Objects.equals(trim(a.title()), trim(b.title()))
                || !Objects.equals(daySet(a), daySet(b))
                || !Objects.equals(a.durationMinutes(), b.durationMinutes())
                || !Objects.equals(a.preferredStartTime(), b.preferredStartTime());
    }

    private static Map<String, PlanTask> byTempId(List<PlanTask> tasks) {
        return tasks.stream().filter(t -> t.tempId() != null)
                .collect(Collectors.toMap(PlanTask::tempId, Function.identity(), (x, y) -> x));
    }

    private static Object daySet(PlanTask t) {
        return t.routineDays() == null ? null : new HashSet<>(t.routineDays());
    }

    private static String trim(String s) {
        return s == null ? null : s.strip();
    }
}
