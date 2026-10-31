import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, isApiError } from "@/lib/client/api";
import { toast } from "@/stores/toastStore";
import type {
  ActivityInput,
  ActualActivity,
  GoalTimeResponse,
  LifePattern,
  MonthlyTimetableResponse,
  ReplanResponse,
  TaskBatchScheduleResponse,
  TaskPriority,
  TaskScheduleResponse,
  TimeBlockTask,
  TimetableResponse,
} from "./types";

export const timetableKeys = {
  all: ["timetable"] as const,
  range: (start: string, end: string) =>
    [...timetableKeys.all, start, end] as const,
  // 'timetable' 아래에 둬서 Task 를 바꾸면 월간도 같이 다시 불러와요
  monthly: (year: number, month: number) =>
    [...timetableKeys.all, "monthly", year, month] as const,
  // 목표별 실천 시간 — 'timetable' 아래라 Task 를 완료·수정하면 같이 다시 불러와요
  goalTime: (start: string, end: string) =>
    [...timetableKeys.all, "goal-time", start, end] as const,
};

/** GET /timetable/goal-time?startDate&endDate (최대 42일) — 캘린더의 "목표별 실천 시간" */
export function useGoalTime(start: string, end: string, enabled = true) {
  return useQuery({
    queryKey: timetableKeys.goalTime(start, end),
    queryFn: () =>
      api.get<GoalTimeResponse>(
        `/v1/timetable/goal-time?startDate=${start}&endDate=${end}`,
      ),
    enabled,
    placeholderData: (prev) => prev,
  });
}

/** GET /timetable/monthly?year&month */
export function useMonthlyTimetable(year: number, month: number) {
  return useQuery({
    queryKey: timetableKeys.monthly(year, month),
    queryFn: () =>
      api.get<MonthlyTimetableResponse>(
        `/v1/timetable/monthly?year=${year}&month=${month}`,
      ),
    placeholderData: (prev) => prev,
  });
}

/** GET /timetable?startDate&endDate (최대 7일) */
export function useTimetable(start: string, end: string) {
  return useQuery({
    queryKey: timetableKeys.range(start, end),
    queryFn: () =>
      api.get<TimetableResponse>(
        `/v1/timetable?startDate=${start}&endDate=${end}`,
      ),
    // 주를 넘길 때 이전 화면을 유지해서 깜빡이지 않게
    placeholderData: (prev) => prev,
  });
}

const errorMessage = (
  e: unknown,
  fallback = "문제가 생겼어요. 다시 시도해 주세요.",
) => (isApiError(e) ? e.message : fallback);

/**
 * 캘린더에서 하는 모든 변경은 끝나면 TimeTable 을 다시 불러와요.
 * (밀려난 블록·덮인 고정 시간·실행률이 같이 바뀌므로 부분 수정보다 다시 조회가 안전)
 */
function useTimetableMutation<V, R>(
  fn: (v: V) => Promise<R>,
  opts: { success?: (r: R, v: V) => string | null } = {},
) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: (r, v) => {
      const msg = opts.success?.(r, v);
      if (msg) toast.success(msg);
    },
    onError: (e) => toast.error(errorMessage(e)),
    onSettled: () => {
      qc.invalidateQueries({ queryKey: timetableKeys.all });
      // 홈의 "오늘 회고 · 다가오는 마감"(features/home 의 homeKeys.all)도 Task 로 계산해요
      qc.invalidateQueries({ queryKey: ["home"] });
    },
  });
}

export function useToggleComplete() {
  return useTimetableMutation(
    (task: Pick<TimeBlockTask, "taskId" | "taskStatus">) =>
      api.patch(
        `/v1/tasks/${task.taskId}/${task.taskStatus === "COMPLETED" ? "cancel-complete" : "complete"}`,
      ),
    {
      success: (_, t) =>
        t.taskStatus === "COMPLETED" ? "완료를 취소했어요." : "완료했어요.",
    },
  );
}

/**
 * 완료 + 실제 시각 기록 ("계획과 다른 시간에 했어요").
 * PATCH /task-actuals/{taskId}/actual 은 실제 시각을 저장하면서 완료 처리도 같이 해요.
 * 실제 시각을 남기지 않은 완료는 "계획한 시각에 했다"고 보고 나의 패턴을 계산해요.
 */
export function useCompleteWithActual() {
  return useTimetableMutation(
    (v: {
      taskId: number;
      actualStartAt: string;
      actualEndAt: string;
      /** 여러 번 나눠서 했으면 각 구간 (2개 이상일 때만 보내요) */
      segments?: { start: string; end: string }[];
    }) =>
      api.patch(
        `/v1/task-actuals/${v.taskId}/actual`,
        v.segments && v.segments.length > 1
          ? { segments: v.segments }
          : { actualStartAt: v.actualStartAt, actualEndAt: v.actualEndAt },
      ),
    { success: () => "완료했어요. 실제로 한 시간도 남겼어요." },
  );
}

export function useDeleteTask() {
  return useTimetableMutation(
    (taskId: number) => api.delete(`/v1/tasks/${taskId}`),
    {
      success: () => "삭제했어요.",
    },
  );
}

export function useUnlockTask() {
  return useTimetableMutation(
    (taskId: number) => api.patch<TimeBlockTask>(`/v1/tasks/${taskId}/unlock`),
    {
      success: () =>
        "AI 자동 배치에 맡겼어요. 다음 자동 배치 때 빈 시간으로 옮겨질 수 있어요.",
    },
  );
}

/** 우선순위만 바꾸기 — 시간은 그대로, 루틴 · 미션은 그날 Task 만 (PATCH /tasks/{id}/priority) */
export function useChangePriority() {
  return useTimetableMutation(
    (v: { taskId: number; taskPriority: TaskPriority }) =>
      api.patch<TimeBlockTask>(`/v1/tasks/${v.taskId}/priority`, {
        taskPriority: v.taskPriority,
      }),
  );
}

export function useUnscheduleTask() {
  return useTimetableMutation(
    (taskId: number) =>
      api.delete<TimeBlockTask>(`/v1/tasks/${taskId}/schedule`),
    {
      success: () => "시간 미정으로 옮겼어요.",
    },
  );
}

/** 시간 변경 (드래그·직접 입력 공통). 저장하면 그 시간에 고정(📌)돼요. */
export function useRescheduleTask() {
  return useTimetableMutation(
    (v: { taskId: number; plannedStartAt: string; plannedEndAt: string }) =>
      api.patch<TaskScheduleResponse>(`/v1/tasks/${v.taskId}/schedule`, {
        plannedStartAt: v.plannedStartAt,
        plannedEndAt: v.plannedEndAt,
      }),
    { success: (r) => scheduleResultMessage(r) },
  );
}

/**
 * 다른 날로 옮기기 (2026-10) — 블록 상세 "다른 날로"(내일 · 모레 · 날짜 지정) · 시간 미정끼리 드래그.
 *  - keepTime true: 그날 같은 시각으로 놓아 봐요. 자리가 없으면 그날 "시간 미정"으로 (응답 plannedStartAt 이 null)
 *  - keepTime false: 그날 "시간 미정"으로
 * 루틴은 시간 미정이 될 수 없어 같은 시각에 자리가 없으면 409 예요.
 */
export function useMoveTaskDate() {
  return useTimetableMutation(
    (v: { taskId: number; date: string; keepTime: boolean; label?: string }) =>
      api.patch<TimeBlockTask>(`/v1/tasks/${v.taskId}/date`, {
        date: v.date,
        keepTime: v.keepTime,
      }),
    {
      success: (r, v) => {
        const day =
          v.label ??
          `${Number(v.date.slice(5, 7))}/${Number(v.date.slice(8, 10))}`;
        if (!r?.plannedStartAt)
          return v.keepTime
            ? `${day} 같은 시간엔 자리가 없어서 그날 시간 미정으로 옮겼어요.`
            : `${day} 시간 미정으로 옮겼어요.`;
        return `${day}로 옮겼어요.`;
      },
    },
  );
}

/** 여러 Task 를 한 번에 옮기기 (묶음 드래그) — 하나라도 자리가 없으면 아무것도 바뀌지 않아요 */
export function useRescheduleBatch() {
  return useTimetableMutation(
    (v: {
      items: { taskId: number; plannedStartAt: string; plannedEndAt: string }[];
    }) =>
      api.patch<TaskBatchScheduleResponse>("/v1/tasks/schedule/batch", {
        items: v.items,
      }),
    {
      success: (r, v) =>
        scheduleResultMessage(r ? { ...r, task: r.tasks[0] } : null).replace(
          "시간을 바꿨어요",
          `${v.items.length}개를 함께 옮겼어요`,
        ),
    },
  );
}

// ── 22시 이월 제안에 답하기 ─────────────────────────────

/** "옮길까요?" → 옮기기 (자동으로 잡은 자리 그대로 확정) */
export function useAcceptCarry() {
  return useTimetableMutation(
    (taskId: number) =>
      api.patch<TimeBlockTask>(`/v1/tasks/${taskId}/carry-over/accept`),
    {
      success: () => "옮겼어요.",
    },
  );
}

/** "옮길까요?" → 안 옮기기 (시간을 비워 그날 "시간 미정"으로) */
export function useDeclineCarry() {
  return useTimetableMutation(
    (taskId: number) =>
      api.patch<TimeBlockTask>(`/v1/tasks/${taskId}/carry-over/decline`),
    {
      success: () =>
        "시간 미정으로 두었어요. 원하는 시간에 끌어다 놓거나 지울 수 있어요.",
    },
  );
}

/** 이월 되돌리기 — 원래 날짜 · 원래 시각으로 (자리가 없으면 그날 시간 미정). 자동 이월이 다시 옮기지 않아요 (2026-10) */
export function useRevertCarry() {
  return useTimetableMutation(
    // from: 누른 이월 흔적이 있는 날 (없으면 마지막 이월의 원래 날). 이미 그날에 있으면 그대로 돌려줘요
    ({ taskId, from }: { taskId: number; from?: string }) =>
      api.patch<TimeBlockTask>(
        `/v1/tasks/${taskId}/carry-over/revert${from ? `?from=${from}` : ""}`,
      ),
    {
      success: (t) =>
        t?.plannedStartAt
          ? "원래 시간으로 되돌렸어요."
          : "원래 날로 되돌렸어요. 시간이 차 있어서 시간 미정에 두었어요.",
    },
  );
}

/** 자동 생성 · 자동 이월 설정 (GET/PATCH /planner-settings) */
export interface PlannerSetting {
  autoGenerateEnabled: boolean;
  nightlyPushEnabled: boolean;
  taskGapMinutes: number;
  /** 못 한 일을 다음 날 아침(기상 시간)에 자동으로 옮길지 — 끄면 그날에 '놓침'으로 남아요 (2026-10) */
  autoCarryEnabled: boolean;
}
const plannerSettingKey = ["planner-setting"] as const;

export function usePlannerSetting() {
  return useQuery({
    queryKey: plannerSettingKey,
    queryFn: () => api.get<PlannerSetting>("/v1/planner-settings"),
  });
}

export function useUpdatePlannerSetting() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (
      patch: Partial<
        Pick<
          PlannerSetting,
          "autoGenerateEnabled" | "nightlyPushEnabled" | "autoCarryEnabled"
        >
      >,
    ) => api.patch<PlannerSetting>("/v1/planner-settings", patch),
    onSuccess: (r) => {
      if (r) qc.setQueryData(plannerSettingKey, r);
    },
    onError: (e) => toast.error(errorMessage(e)),
    onSettled: () => qc.invalidateQueries({ queryKey: plannerSettingKey }),
  });
}

/** 그날 이월 제안 모두 옮기기 */
export function useAcceptAllCarry() {
  return useTimetableMutation(
    (date: string) =>
      api.patch<number>(`/v1/tasks/carry-over/accept-all?date=${date}`),
    {
      success: (n) => `${n ?? 0}개를 옮겼어요.`,
    },
  );
}

/** 드래그 결과 안내 문장: 밀려난 블록 · 덮은 고정 시간 */
export function scheduleResultMessage(r: TaskScheduleResponse | null) {
  if (!r) return "시간을 바꿨어요.";
  const parts: string[] = [];
  if (r.coveredFixedBlocks?.length)
    parts.push(
      `${r.coveredFixedBlocks.map((c) => c.title).join("·")} 시간을 덮었어요`,
    );
  if (r.moved?.length)
    parts.push(`${r.moved.length}개 블록을 다른 시간으로 옮겼어요`);
  if (r.unscheduled?.length)
    parts.push(`${r.unscheduled.length}개는 빈 시간이 없어 시간 미정이 됐어요`);
  return parts.length
    ? `시간을 바꿨어요 · ${parts.join(", ")}.`
    : "시간을 바꿨어요.";
}

/** 고정 시간 — 그날만 변경 / 비우기 / 되돌리기 */
export function usePatternOverride() {
  return useTimetableMutation(
    (v: {
      lifePatternId: number;
      targetDate: string;
      startTime?: string;
      endTime?: string;
      skipped?: boolean;
    }) =>
      api.put(
        `/v1/life-patterns/${v.lifePatternId}/overrides/${v.targetDate}`,
        {
          startTime: v.startTime,
          endTime: v.endTime,
          skipped: v.skipped,
        },
      ),
    {
      success: (_, v) =>
        v.skipped ? "이날만 비웠어요." : "이날만 시간을 바꿨어요.",
    },
  );
}

export function useDeletePatternOverride() {
  return useTimetableMutation(
    (v: { lifePatternId: number; targetDate: string }) =>
      api.delete(
        `/v1/life-patterns/${v.lifePatternId}/overrides/${v.targetDate}`,
      ),
    { success: () => "기본 시간으로 되돌렸어요." },
  );
}

export const lifePatternKeys = { list: ["life-patterns"] as const };

// ─── 범위 골라 바꾸기 (이날만 / 이번 주 / 앞으로) ───────────────────

/** DAY 이날만 · WEEK 이번 주 · FOLLOWING 앞으로 계속(고정 시간은 "매일") · WEEKDAY 앞으로 이 요일마다 (고정 시간 전용) */
export type ChangeScope = "DAY" | "WEEK" | "FOLLOWING" | "WEEKDAY";

export interface RoutineScheduleResponse {
  scope: ChangeScope;
  result: TaskScheduleResponse;
  /** 같이 옮긴 다른 날짜 */
  changedDates: string[];
  /** 잠긴 일정과 겹쳐 건너뛴 날짜 (그날은 원래 시간 그대로) */
  skippedDates: string[];
}

const skippedNote = (dates: string[]) =>
  dates.length
    ? ` ${dates.map((d) => `${Number(d.slice(5, 7))}/${Number(d.slice(8, 10))}`).join(", ")}은 다른 일정과 겹쳐 그대로예요.`
    : "";

/** 루틴 블록 시간 변경 + 범위. DAY 는 기존 드래그와 같아요 */
export function useRoutineScopedReschedule() {
  return useTimetableMutation(
    (v: {
      taskId: number;
      plannedStartAt: string;
      plannedEndAt: string;
      scope: ChangeScope;
    }) =>
      api.patch<RoutineScheduleResponse>(
        `/v1/tasks/${v.taskId}/routine-schedule`,
        {
          plannedStartAt: v.plannedStartAt,
          plannedEndAt: v.plannedEndAt,
          scope: v.scope,
        },
      ),
    {
      success: (r, v) => {
        if (!r || v.scope === "DAY")
          return scheduleResultMessage(r?.result ?? null);
        const n = r.changedDates.length + 1;
        const head =
          v.scope === "WEEK"
            ? `이번 주 루틴 ${n}일의 시간을 바꿨어요.`
            : `앞으로의 루틴 시간을 바꿨어요.`;
        return head + skippedNote(r.skippedDates);
      },
    },
  );
}

/** 고정 시간 변경 + 범위. FOLLOWING 은 기본값이 바뀌므로 패턴 목록도 다시 불러와요 */
export function usePatternScopedChange() {
  const qc = useQueryClient();
  return useTimetableMutation(
    async (v: {
      lifePatternId: number;
      targetDate: string;
      startTime: string;
      endTime: string;
      scope: ChangeScope;
      /** 블록이 targetDate 하루 앞(-1)·뒤(+1)에 시작 (기상·취침 드래그) */
      startDayOffset?: number;
    }) => {
      const r = await api.put(
        `/v1/life-patterns/${v.lifePatternId}/scoped-change`,
        {
          targetDate: v.targetDate,
          startTime: v.startTime,
          endTime: v.endTime,
          scope: v.scope,
          startDayOffset: v.startDayOffset ?? 0,
        },
      );
      if (v.scope === "FOLLOWING" || v.scope === "WEEKDAY")
        qc.invalidateQueries({ queryKey: lifePatternKeys.list });
      return r;
    },
    {
      success: (_, v) =>
        v.scope === "DAY"
          ? "이날만 시간을 바꿨어요."
          : v.scope === "WEEK"
            ? "이번 주 시간을 바꿨어요."
            : v.scope === "WEEKDAY"
              ? "앞으로 이 요일의 시간을 바꿨어요. 지난 기록은 그대로예요."
              : "앞으로 매일의 시간을 바꿨어요. 지난 기록은 그대로예요.",
    },
  );
}

/** 기본 생활 패턴 목록 */
export function useLifePatterns(enabled = true) {
  return useQuery({
    queryKey: lifePatternKeys.list,
    queryFn: async () =>
      (await api.get<LifePattern[]>("/v1/life-patterns")) ?? [],
    enabled,
    staleTime: 5 * 60_000,
  });
}

/**
 * "다시 배치" — 그날의 자동 배치 블록과 시간 미정 Task 를 다시 놓아요 (잠긴 블록은 그대로).
 * 지난 날짜는 400 이라 오늘 이후만 보내요.
 */
export function useReplan() {
  return useTimetableMutation(
    async (dates: string[]) => {
      const out: ReplanResponse[] = [];
      for (const d of dates)
        out.push(await api.post<ReplanResponse>(`/v1/daily-plans/${d}/replan`));
      return out;
    },
    {
      success: (res) => {
        const n = res.reduce(
          (sum, r) => sum + r.placed.length + r.movedToOtherDays.length,
          0,
        );
        return n
          ? `${n}개 블록을 다시 배치했어요.`
          : "다시 배치할 블록이 없었어요.";
      },
    },
  );
}

// ─── AI로 TimeTable 배치하기 — 미리보기(점선) → 확인 → 확정 ──────────────

export interface FillProposal {
  taskId: number;
  date: string;
  start: string;
  end: string;
  reason?: string | null;
}

export interface FillPreviewResponse {
  proposals: FillProposal[];
  /** 빈 시간이 없어 제안하지 못한 Task */
  unscheduled: number[];
}

export type FillPreviewTarget =
  { startDate: string; endDate: string } | { taskIds: number[] };

/** 저장하지 않고 어디에 놓일지만 받아와요 — 기간 전체(AI로 배치하기) 또는 정한 Task 들만(방금 추가한 Task) */
export function useFillPreview() {
  return useMutation({
    mutationFn: (v: FillPreviewTarget) =>
      "taskIds" in v
        ? api.post<FillPreviewResponse>("/v1/daily-plans/fill/preview-tasks", {
            taskIds: v.taskIds,
          })
        : api.post<FillPreviewResponse>(
            `/v1/daily-plans/fill/preview?startDate=${v.startDate}&endDate=${v.endDate}`,
          ),
    onError: (e) => toast.error(errorMessage(e)),
  });
}

/** 점선 제안을 최종 자리로 확정 (그대로 둔 것 = AI 배치, 옮긴 것 = 📌 직접 정한 시간) */
export function useFillApply() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (v: {
      items: {
        taskId: number;
        proposedStart: string;
        proposedEnd: string;
        start: string;
        end: string;
        reason?: string | null;
      }[];
    }) =>
      api.post<{ placed: number; skipped: number[] }>(
        "/v1/daily-plans/fill/apply",
        v,
      ),
    onSuccess: (r) => {
      if (!r) return;
      toast.success(
        `${r.placed}개 Task를 배치했어요.` +
          (r.skipped.length
            ? ` ${r.skipped.length}개는 그사이 자리가 바뀌어 시간 미정으로 남겼어요.`
            : ""),
      );
    },
    onError: (e) => toast.error(errorMessage(e)),
    onSettled: () => {
      qc.invalidateQueries({ queryKey: timetableKeys.all });
      qc.invalidateQueries({ queryKey: ["goals"] });
    },
  });
}

// ─── AI로 TimeTable 배치하기 (바로 저장 — Task 추가 화면용) ──────────────

export interface FillResponse {
  days: { date: string; placed: number; unscheduled: number }[];
  placedCount: number;
  unscheduledCount: number;
}

/**
 * POST /daily-plans/fill?startDate&endDate (최대 7일)
 * 직접 정한 시간(📌)·이미 배치된 블록·고정 시간은 그대로 두고, 시간 미정 Task 만 남은 빈 시간에 넣어요.
 * 오늘은 지금 + 10분 이후만 써요.
 */
export function useFillTimetable() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (v: { startDate: string; endDate: string }) =>
      api.post<FillResponse>(
        `/v1/daily-plans/fill?startDate=${v.startDate}&endDate=${v.endDate}`,
      ),
    onSuccess: (r) => {
      if (!r) return;
      if (!r.placedCount && !r.unscheduledCount)
        toast.show("배치할 시간 미정 Task가 없었어요.");
      else if (!r.placedCount)
        toast.error(
          `빈 시간이 없어 ${r.unscheduledCount}개는 시간 미정으로 남았어요.`,
        );
      else
        toast.success(
          `${r.placedCount}개 Task를 빈 시간에 배치했어요.` +
            (r.unscheduledCount
              ? ` ${r.unscheduledCount}개는 빈 시간이 없어 시간 미정으로 남았어요.`
              : ""),
        );
    },
    onError: (e) => toast.error(errorMessage(e)),
    onSettled: () => {
      qc.invalidateQueries({ queryKey: timetableKeys.all });
      // 목표 상세의 관련 Task 도 시간이 바뀌어요 (goal/api 를 import 하면 순환 참조라 키를 그대로 써요)
      qc.invalidateQueries({ queryKey: ["goals"] });
    },
  });
}

// ─── 실제로 한 일 ────────────────────────────────────────────────

export const activityKeys = {
  all: ["activities"] as const,
  range: (start: string, end: string) =>
    [...activityKeys.all, start, end] as const,
};

/** 기간(시작일~종료일, 최대 42일)과 겹치는 실제 기록 */
export function useActivities(start: string, end: string, enabled = true) {
  return useQuery({
    queryKey: activityKeys.range(start, end),
    queryFn: async () =>
      (await api.get<ActualActivity[]>(
        `/v1/activities?startDate=${start}&endDate=${end}`,
      )) ?? [],
    enabled,
    placeholderData: (prev) => prev,
  });
}

/** 추가(activityId 없음) · 수정 */
export function useSaveActivity() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      activityId,
      ...body
    }: ActivityInput & { activityId?: number }) =>
      activityId
        ? api.put<ActualActivity>(`/v1/activities/${activityId}`, body)
        : api.post<ActualActivity>("/v1/activities", body),
    onSuccess: (_, v) =>
      toast.success(
        v.activityId ? "기록을 수정했어요." : "실제로 한 일을 기록했어요.",
      ),
    onError: (e) => toast.error(errorMessage(e)),
    onSettled: () => qc.invalidateQueries({ queryKey: activityKeys.all }),
  });
}

export function useDeleteActivity() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (activityId: number) =>
      api.delete(`/v1/activities/${activityId}`),
    onSuccess: () => toast.success("기록을 지웠어요."),
    onError: (e) => toast.error(errorMessage(e)),
    onSettled: () => qc.invalidateQueries({ queryKey: activityKeys.all }),
  });
}

// ── 캘린더의 습관 블록 체크 (V56) ─────────────────────────
/** 습관 블록을 눌러 체크 · 체크 풀기 → 캘린더 · 습관 · 홈을 다시 불러와요 */
export function useToggleHabitBlock() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      habitId,
      date,
      checked,
    }: {
      habitId: number;
      date: string;
      checked: boolean;
    }) =>
      checked
        ? api.delete(`/v1/habits/${habitId}/checks/${date}`)
        : api.put(`/v1/habits/${habitId}/checks/${date}`),
    onError: (e) =>
      toast.error(
        isApiError(e) ? e.message : "체크하지 못했어요. 다시 시도해 주세요.",
      ),
    onSettled: () => {
      qc.invalidateQueries({ queryKey: timetableKeys.all });
      qc.invalidateQueries({ queryKey: ["habits"] });
      qc.invalidateQueries({ queryKey: ["home"] });
    },
  });
}
