"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import {
  Check,
  ChevronLeft,
  ChevronRight,
  Plus,
  Repeat,
} from "@/components/icons";
import { Undo2 } from "lucide-react";
import { rectOf, type AnchorRect } from "@/components/ui/Popover";
import { ActionMenu } from "./ActionMenu";
import { PageHeader } from "@/components/layout/PageHeader";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Spinner } from "@/components/ui/Spinner";
import { cn } from "@/lib/cn";
import { segmentClass, segmentedClass } from "@/components/ui/SegmentedTabs";
import { iconButtonClass } from "@/components/ui/IconButton";
import { TaskAddPanel } from "@/features/brain-dump/ui/TaskAddPanel";
import { track } from "@/lib/client/analytics";
import { dateRelationOf, errorStatusOf } from "@/features/home/analytics";
import { useActivities, useRevertCarry, useTimetable } from "../api";
import type { CarriedOutBlock, TimeBlockTask } from "../types";
import type { DragMode, DragSource, DropTarget } from "./useGridDrag";
import { visibleHours } from "../layout";
import {
  addDays,
  addMonths,
  formatRange,
  isYmd,
  minutesToTime,
  startOfWeek,
  todayKst,
} from "../time";
import { HATCH } from "./Blocks";
import { MonthView } from "./MonthView";
import { AutoPlaceBar } from "./AutoPlaceBar";
import { AutoCarryToggle } from "./AutoCarryToggle";
import {
  ScheduleAiBar,
  useScheduleEditApply,
  type ScheduleAiBarHandle,
  type ScheduleEditOp,
  type ScheduleEditPreview,
} from "./ScheduleAiBar";
import { ScheduleAiPanel, applyAiEditPreview } from "./ScheduleAiPanel";
import { DayTaskList } from "./DayTaskList";
import { GoalTimeStats } from "./GoalTimeStats";
import { TimeGrid } from "./TimeGrid";
import { useTimetableInteractions } from "./useTimetableInteractions";
import { useSlotAddMenu } from "./useSlotAddMenu";

export type CalendarMode = "day" | "week" | "month";
type AddMode = "ai" | "manual" | "log";

/** URL(?view=week&date=2026-11-18) ↔ 화면 상태 */
function useCalendarParams() {
  const params = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();
  const rawView = params.get("view");
  const view: CalendarMode =
    rawView === "day" || rawView === "month" ? rawView : "week";
  const rawDate = params.get("date");
  const date = isYmd(rawDate) ? rawDate : todayKst();
  // ?add=ai|manual|log → 오른쪽 Task 추가 패널 (사이드바 "Task 추가"가 여기로 와요)
  const rawAdd = params.get("add");
  const add: AddMode | null =
    rawAdd === "ai" || rawAdd === "manual" || rawAdd === "log"
      ? rawAdd
      : rawAdd
        ? "ai"
        : null;

  const go = useCallback(
    (next: { view?: CalendarMode; date?: string; add?: AddMode | null }) => {
      const q = new URLSearchParams(params.toString());
      q.set("view", next.view ?? view);
      q.set("date", next.date ?? date);
      const nextAdd = next.add === undefined ? add : next.add;
      if (nextAdd) q.set("add", nextAdd);
      else q.delete("add");
      router.replace(`${pathname}?${q.toString()}`, { scroll: false });
    },
    [params, router, pathname, view, date, add],
  );
  return { view, date, add, go };
}

export function CalendarView() {
  const { view, date, add, go } = useCalendarParams();
  // 패널에서 방금 추가한 Task — 캘린더에 NEW 로 표시
  const [newIds, setNewIds] = useState<ReadonlySet<number>>(new Set());
  // AI 메뉴 (명세 11.1)
  // 계획 할 일 추가 패널에 미리 채울 날짜 · 시각
  const [prefill, setPrefill] = useState<
    { date: string; startTime: string; endTime: string } | undefined
  >();
  // 월간은 오른쪽 패널에 보여줄 "선택한 날" 하루만 불러와요 (달 전체 요약은 MonthView 가 따로)
  const start = view === "week" ? startOfWeek(date) : date;
  const end = view === "week" ? addDays(start, 6) : date;
  const { data, isPending, isError, error, refetch, isFetching } = useTimetable(
    start,
    end,
  );

  // 표시 중인 데이터가 지금 보는 기간의 것인지 (placeholderData 로 이전 주가 잠깐 보일 수 있음)
  const rawDays = useMemo(
    () => (data?.days ?? []).filter((d) => d.date >= start && d.date <= end),
    [data, start, end],
  );
  // 실제로 한 일 (월간은 시간 격자가 없어서 안 불러요)
  const activities = useActivities(start, end, view !== "month");
  const acts = useMemo(() => activities.data ?? [], [activities.data]);
  // + 메뉴 · 빈 칸 왼쪽 클릭/우클릭 메뉴 · Task 우클릭 메뉴 (명세 11) — 홈 오늘 일정과 같은 훅
  const today = todayKst();
  const slotMenu = useSlotAddMenu({
    viewMode: view,
    viewDate: date,
    today,
    singleDay: view === "day",
    track: (event, props) => track(`calendar.${event}`, props),
    onAdd: (mode, at, nextPrefill) => {
      const panelView = view === "month" ? "week" : view;
      setPrefill(mode === "manual" ? nextPrefill : undefined);
      go({
        add: mode,
        view: panelView,
        ...(mode === "ai" && at ? { date: at.date } : {}),
      });
    },
    patternEntrySource: (trigger) =>
      trigger === "left_click" ? "calendar_empty_tap" : "calendar_context_menu",
  });
  const {
    days,
    selection,
    onSelectTask,
    onSelectFixed,
    onSelectActivity,
    onEmptyClick,
    openActivity,
    openTaskDetail,
    requestDeleteTask,
    onDrop,
    close,
    toggleComplete,
    overlays,
    groupIds,
    previewFill,
    fillPreviewing,
    proposing,
    focusIds,
    fadeOthers,
    gridEvents,
    completeWithTime,
  } = useTimetableInteractions(rawDays, date, {
    sidePanel: !!add,
    surface: "calendar",
    onEmptyMenu: slotMenu.openSlot,
  });
  const { startHour, endHour } = useMemo(
    () => visibleHours(days, acts),
    [days, acts],
  );

  // ── 이동 ──
  const move = (n: number) => {
    close();
    const next =
      view === "month"
        ? addMonths(date, n)
        : addDays(date, n * (view === "week" ? 7 : 1));
    track("calendar.date_nav_click", {
      view_mode: view,
      date_action: n < 0 ? "prev" : "next",
      view_date: next,
      date_relation: dateRelationOf(next),
    });
    go({ date: next });
  };
  const unit = view === "month" ? "달" : view === "week" ? "주" : "날";
  // ── AI로 일정 옮기기 미리보기 (2026-10) ──
  const aiBarRef = useRef<ScheduleAiBarHandle | null>(null);
  const [aiEdit, setAiEdit] = useState<{
    preview: ScheduleEditPreview;
    ops: ScheduleEditOp[];
    picked: boolean[];
    focus: number | null;
  } | null>(null);
  const aiApply = useScheduleEditApply();
  const aiDays = useMemo(
    () =>
      aiEdit
        ? applyAiEditPreview(days, aiEdit.ops, aiEdit.picked, today)
        : days,
    [aiEdit, days, today],
  );
  const aiFocusIds = useMemo(
    () =>
      new Set(
        aiDays.flatMap((d) =>
          d.tasks.filter((t) => t.proposed).map((t) => t.taskId),
        ),
      ),
    [aiDays],
  );
  /** 캘린더의 점선 제안을 누르면 패널의 그 항목으로 */
  const onSelectAiEdit = (t: TimeBlockTask) => {
    if (!aiEdit || !t.proposed) return;
    const i = aiEdit.ops.findIndex(
      (o, j) =>
        aiEdit.picked[j] &&
        ((o.type === "MOVE_TASK" && o.targetId === t.taskId) ||
          (o.type === "MOVE_ROUTINE" && o.targetId === t.routineId)),
    );
    if (i >= 0) {
      track("calendar_ai.block_click", {
        change_kind: aiEdit.preview.changes[i]?.kind,
      });
      setAiEdit({ ...aiEdit, focus: i });
    }
  };
  /**
   * 점선 제안을 끌어 놓으면 패널의 그 항목 값을 바꿔요 (2026-10) — 아직 저장하지 않아요
   *  - Task: 날짜 · 시작 (위아래 끝을 끌면 길이도)
   *  - 루틴: 시작 · 길이만 (요일은 패널에서) — 어느 날 블록을 끌어도 그 루틴 전체가 같이 움직여요
   *  - 시간 미정 칸에 놓으면 그 항목을 빼요
   */
  const onAiDrop = (source: DragSource, target: DropTarget, mode: DragMode) => {
    if (!aiEdit || source.kind !== "task" || !source.task.proposed) return;
    const t = source.task;
    const i = aiEdit.ops.findIndex(
      (o, j) =>
        aiEdit.picked[j] &&
        ((o.type === "MOVE_TASK" && o.targetId === t.taskId) ||
          (o.type === "MOVE_ROUTINE" && o.targetId === t.routineId)),
    );
    if (i < 0) return;
    const kind = aiEdit.preview.changes[i]?.kind;
    if (target.type !== "grid") {
      track("calendar_ai.item_toggle", { change_kind: kind, checked: false });
      setAiEdit({
        ...aiEdit,
        picked: aiEdit.picked.map((p, j) => (j === i ? false : p)),
        focus: i,
      });
      return;
    }
    track("calendar_ai.item_edit", { change_kind: kind, edit_field: "drag" });
    const o = aiEdit.ops[i];
    const next: ScheduleEditOp = {
      ...o,
      startTime: minutesToTime(((target.start % 1440) + 1440) % 1440),
      ...(o.type === "MOVE_TASK" ? { date: target.date } : {}),
      ...(mode !== "move"
        ? { durationMinutes: target.end - target.start }
        : {}),
    };
    setAiEdit({
      ...aiEdit,
      ops: aiEdit.ops.map((x, j) => (j === i ? next : x)),
      focus: i,
    });
  };
  const closeAiEdit = (method: "x_button" | "cancel_button" | "esc") => {
    if (aiApply.isPending || !aiEdit) return;
    track("calendar_ai.cancel", {
      close_method: method,
      change_count: aiEdit.preview.changes.length,
    });
    setAiEdit(null);
  };
  const applyAiEdit = () => {
    if (!aiEdit) return;
    const chosen = aiEdit.ops.filter((_, i) => aiEdit.picked[i]);
    const edited = chosen.filter((o) => {
      const orig = aiEdit.preview.changes[aiEdit.ops.indexOf(o)]?.op;
      return (
        orig &&
        (orig.startTime !== o.startTime ||
          orig.date !== o.date ||
          orig.durationMinutes !== o.durationMinutes ||
          (orig.days ?? []).join() !== (o.days ?? []).join())
      );
    }).length;
    track("calendar_ai.apply_click", {
      selected_count: chosen.length,
      change_count: aiEdit.preview.changes.length,
      edited_count: edited,
    });
    aiApply.mutate(
      { ops: chosen, startDate: start, endDate: end },
      {
        onSuccess: (r) => {
          track("calendar_ai.apply_result", {
            result: "success",
            applied_count: r?.applied ?? 0,
            skipped_count: r?.skipped?.length ?? 0,
          });
          setAiEdit(null);
          aiBarRef.current?.clear();
        },
        onError: (e) =>
          track("calendar_ai.apply_result", {
            result: "fail",
            error_status: errorStatusOf(e),
          }),
      },
    );
  };

  /**
   * 다른 날로 옮겨진 Task 흔적 → 메뉴 (2026-10)
   *  - 그때 했어요: 원래 날로 되돌린 뒤 실제로 한 시간 입력 (계획과 다른 시간 · 나눠서 한 것도)
   *  - 원래 시간으로 되돌리기 (자리가 없으면 그날 시간 미정) · 옮긴 곳 보기
   */
  const [carriedMenu, setCarriedMenu] = useState<{
    c: CarriedOutBlock;
    fromDate: string;
    anchor: AnchorRect;
  } | null>(null);
  const revertCarry = useRevertCarry();
  const onCarriedClick = (
    c: CarriedOutBlock,
    fromDate: string,
    anchor?: AnchorRect,
  ) => {
    track("timetable.carried_trace_click", {
      surface: "calendar",
      view_mode: view,
      task_id: c.taskId,
      is_scheduled: !!c.start,
      day_offset: Math.round(
        (Date.parse(c.toDate) - Date.parse(fromDate)) / 86_400_000,
      ),
    });
    close();
    if (anchor) setCarriedMenu({ c, fromDate, anchor });
    else go({ date: c.toDate });
  };
  const carriedAction = (action: "done_then" | "revert" | "go") => {
    const m = carriedMenu;
    if (!m) return;
    setCarriedMenu(null);
    track("timetable.carried_trace_action", {
      surface: "calendar",
      task_id: m.c.taskId,
      carry_action: action,
    });
    if (action === "go") return go({ date: m.c.toDate });
    revertCarry.mutate(
      { taskId: m.c.taskId, from: m.fromDate },
      {
        onSuccess: (t) => {
          if (action === "done_then" && t?.plannedStartAt) completeWithTime(t);
        },
      },
    );
  };

  // calendar.pageview — 처음 그려진 직후 한 번만 (보기·날짜가 바뀌어도 다시 안 보냄)
  const pageviewSent = useRef(false);
  useEffect(() => {
    if (pageviewSent.current) return;
    pageviewSent.current = true;
    track("calendar.pageview", {
      view_mode: view,
      view_date: date,
      date_relation: dateRelationOf(date),
      add_mode: add ?? undefined,
    });
  }, [view, date, add]);

  // calendar.load_fail — 일간·주간에서 실패 카드가 보이게 될 때 한 번
  const loadFailed = view !== "month" && !isPending && isError && !days.length;
  const errorStatus = errorStatusOf(error);
  const loadFailSent = useRef(false);
  useEffect(() => {
    if (!loadFailed) {
      loadFailSent.current = false;
      return;
    }
    if (loadFailSent.current) return;
    loadFailSent.current = true;
    track("calendar.load_fail", {
      view_mode: view,
      view_date: date,
      error_status: errorStatus,
    });
  }, [loadFailed, view, date, errorStatus]);

  return (
    // 패널이 열려 있으면 xl 이상에서는 캘린더가 패널 폭만큼 비켜서 나란히 보여요
    // 모바일: 페이지 전체 흰 바탕 (명세 9.1) — 바깥 여백(px-5 · py-6)까지 덮어요
    <div
      className={cn(
        (add || aiEdit) && "xl:pr-[420px]",
        "max-lg:bg-bg-surface max-lg:-mx-5 max-lg:-mt-6 max-lg:min-h-[calc(100dvh-48px-var(--tabbar-h))] max-lg:-mb-6 max-lg:px-5 max-lg:pt-6 max-lg:pb-6",
      )}
    >
      <PageHeader
        title="캘린더"
        description="블록을 눌러 배치 이유를 보고, 끌어서 시간을 바꿔요. 지난 빈 시간을 누르면 실제로 한 일을 남길 수 있어요. Shift를 누른 채로 TimeBlock을 여러 개 선택하면 한 번에 수정할 수 있어요."
        actions={
          <div className="flex items-center gap-2">
            {/* 못 한 일 자동 이월 켜기/끄기 (2026-10 · 설정에도 있어요) */}
            {view !== "month" && <AutoCarryToggle variant="compact" />}
            {!add && (
              <Button
                variant="primary"
                size="sm"
                aria-haspopup="menu"
                // + 메뉴(3가지)를 열어요 (명세 11.2) — 메뉴 열기는 이벤트 없음, 항목을 고를 때 보내요
                onClick={(e) => slotMenu.openHeader(rectOf(e.currentTarget))}
              >
                <Plus className="size-4" />
                Task 추가
              </Button>
            )}
            <div className={segmentedClass()} role="tablist" aria-label="보기">
              {(
                [
                  ["day", "일간"],
                  ["week", "주간"],
                  ["month", "월간"],
                ] as const
              ).map(([v, label]) => (
                <button
                  key={v}
                  type="button"
                  role="tab"
                  aria-selected={view === v}
                  onClick={() => {
                    if (v !== view)
                      track("calendar.view_change", {
                        view_mode: v,
                        from_view: view,
                        view_date: date,
                      });
                    close();
                    go({ view: v });
                  }}
                  className={segmentClass(view === v)}
                >
                  {label}
                </button>
              ))}
            </div>
          </div>
        }
      />

      {/* 기간 이동 */}
      <div className="mt-5 mb-3 flex flex-wrap items-center gap-2">
        <Button
          variant="secondary"
          size="sm"
          onClick={() => {
            track("calendar.date_nav_click", {
              view_mode: view,
              date_action: "today",
              view_date: today,
              date_relation: "today",
            });
            go({ date: today });
          }}
          disabled={
            view === "month" ? date === today : start <= today && today <= end
          }
        >
          오늘
        </Button>
        <div className="flex">
          <button
            type="button"
            onClick={() => move(-1)}
            aria-label={`이전 ${unit}`}
            className={cn(
              iconButtonClass("md"),
              "max-lg:size-7 max-lg:[&_svg]:size-4",
            )}
          >
            <ChevronLeft className="size-5" />
          </button>
          <button
            type="button"
            onClick={() => move(1)}
            aria-label={`다음 ${unit}`}
            className={cn(
              iconButtonClass("md"),
              "max-lg:size-7 max-lg:[&_svg]:size-4",
            )}
          >
            <ChevronRight className="size-5" />
          </button>
        </div>
        <h2 className="text-h4 max-lg:text-h3" aria-live="polite">
          {view === "month"
            ? `${date.slice(0, 4)}년 ${Number(date.slice(5, 7))}월`
            : view === "week"
              ? formatRange(start, end)
              : formatRange(date, date)}
        </h2>
        {isFetching && !isPending && (
          <Spinner className="text-ink-4 size-3.5" />
        )}
        {view !== "month" && <Legend />}
      </div>

      {view === "month" ? (
        <MonthView
          date={date}
          today={today}
          dayDetail={days.find((d) => d.date === date)}
          dayLoading={isFetching}
          onSelectDate={(d) => {
            close();
            go({ date: d });
          }}
          onOpenDay={(d) => {
            close();
            go({ view: "day", date: d });
          }}
          onSelectTask={onSelectTask}
          onToggleComplete={(t) => toggleComplete.mutate(t)}
          togglingId={
            toggleComplete.isPending
              ? (toggleComplete.variables?.taskId ?? null)
              : null
          }
        />
      ) : isPending ? (
        <Card className="grid min-h-[420px] place-items-center">
          <Spinner className="text-ink-3 size-6" />
        </Card>
      ) : isError && !days.length ? (
        <Card className="grid min-h-[320px] place-items-center p-8 text-center">
          <div>
            <p className="font-bold">TimeTable을 불러오지 못했어요</p>
            <p className="text-ink-3 mt-1 text-sm">
              {error instanceof Error ? error.message : ""}
            </p>
            <Button
              variant="secondary"
              className="mt-4"
              onClick={() => {
                track("calendar.retry_click", {
                  view_mode: view,
                  error_status: errorStatus,
                });
                refetch();
              }}
            >
              다시 시도
            </Button>
          </div>
        </Card>
      ) : (
        <>
          {/* 여러 목표의 루틴 · 일정을 한 문장으로 옮기기 (보고 있는 주/날 기준) */}
          <ScheduleAiBar
            startDate={start}
            endDate={end}
            viewMode={view}
            handleRef={aiBarRef}
            onPreview={(p) => {
              close();
              if (add) go({ add: null });
              // 같은 날 그대로면 date 가 비어 와요 → 패널 날짜 칸이 비지 않게 지금 날짜로 채워요
              const dateOf = (id: number) =>
                rawDays.find((d) => d.tasks.some((t) => t.taskId === id))
                  ?.date ?? null;
              setAiEdit({
                preview: p,
                ops: p.changes.map((c) =>
                  c.op.type === "MOVE_TASK" && !c.op.date
                    ? { ...c.op, date: dateOf(c.op.targetId) }
                    : { ...c.op },
                ),
                picked: p.changes.map(() => true),
                focus: null,
              });
            }}
          />
          <AutoPlaceBar
            days={rawDays}
            today={today}
            onPreview={previewFill}
            loading={fillPreviewing}
            proposing={proposing}
            viewMode={view}
          />
          {/* 주간: 이번 주 목표별 실천 시간 — 격자 위 한 줄 (2026-10) */}
          {view === "week" && (
            <GoalTimeStats
              start={start}
              end={end}
              viewMode="week"
              periodLabel={start <= today && today <= end ? "이번 주" : "이 주"}
              variant="inline"
              className="mb-3"
            />
          )}
          {/* 일간: TimeTable(계획 | 실제) 오른쪽에 그날 할 일 목록 — Task 추가 패널이 열려 있으면 자리가 좁아 숨겨요 */}
          <div
            className={cn(
              view === "day" &&
                !add &&
                "grid items-start gap-4 lg:grid-cols-[minmax(0,1fr)_300px]",
              // 주간 모바일: 머리 줄 아래(날짜 줄 · 격자)는 양옆 10 (명세 10.3)
              view === "week" && "max-lg:-mx-2.5",
            )}
          >
            <TimeGrid
              {...gridEvents}
              allowFutureEmpty
              days={aiEdit ? aiDays : days}
              startHour={startHour}
              endHour={endHour}
              selection={selection}
              onSelectTask={aiEdit ? onSelectAiEdit : onSelectTask}
              onSelectFixed={onSelectFixed}
              activities={acts}
              onSelectActivity={onSelectActivity}
              onEmptyClick={onEmptyClick}
              // AI로 일정 옮기기 확인 중엔 점선 제안만 끌어서 자리를 고쳐요 (저장은 [적용] 때)
              onDrop={aiEdit ? onAiDrop : onDrop}
              onDragStart={close}
              groupIds={groupIds}
              highlightIds={newIds}
              focusIds={aiEdit ? aiFocusIds : focusIds}
              fadeOthers={aiEdit ? true : fadeOthers}
              onDayClick={(d) => {
                track("calendar.day_header_click", {
                  view_date: d,
                  date_relation: dateRelationOf(d),
                });
                close();
                go({ view: "day", date: d });
              }}
              planVsActual={view === "day"}
              onCarriedClick={onCarriedClick}
              onEmptyContextMenu={(d, minutes, e) =>
                slotMenu.onEmptyContextMenu(d, minutes, e, close)
              }
              onTaskContextMenu={(t, e) =>
                slotMenu.onTaskContextMenu(t, e, close)
              }
            />
            {view === "day" && !add && (
              <div className="flex flex-col gap-3 lg:sticky lg:top-4">
                <DayTaskList
                  day={days.find((d) => d.date === date)}
                  today={today}
                  onSelect={onSelectTask}
                  onToggle={(t) => toggleComplete.mutate(t)}
                  togglingId={
                    toggleComplete.isPending
                      ? (toggleComplete.variables?.taskId ?? null)
                      : null
                  }
                  onCarriedClick={onCarriedClick}
                />
                {/* 오늘 하루 목표별로 얼마나 썼는지 — 참고용 작은 막대 (2026-10) */}
                <GoalTimeStats
                  start={date}
                  end={date}
                  viewMode="day"
                  periodLabel={date === today ? "오늘" : "이날"}
                />
              </div>
            )}
          </div>
        </>
      )}

      {overlays}

      {/* 이월 흔적 메뉴 (2026-10) */}
      {carriedMenu && (
        <ActionMenu
          anchor={carriedMenu.anchor}
          onClose={() => setCarriedMenu(null)}
          label="옮겨진 일"
          items={[
            {
              key: "done_then",
              label: "그때 했어요",
              hint: "원래 날로 되돌리고 실제로 한 시간을 넣어 완료해요",
              icon: <Check />,
              disabled: !carriedMenu.c.start || revertCarry.isPending,
              onSelect: () => carriedAction("done_then"),
            },
            {
              key: "revert",
              label: "원래 시간으로 되돌리기",
              hint: "자리가 차 있으면 그날 시간 미정으로 가요. 다시 자동으로 옮기지 않아요",
              icon: <Undo2 />,
              disabled: revertCarry.isPending,
              onSelect: () => carriedAction("revert"),
            },
            {
              key: "go",
              label: `옮긴 곳 보기 (${Number(carriedMenu.c.toDate.slice(5, 7))}/${Number(carriedMenu.c.toDate.slice(8, 10))})`,
              icon: <ChevronRight />,
              onSelect: () => carriedAction("go"),
            },
          ]}
        />
      )}
      {/* + 메뉴 · 빈 칸 메뉴 · Task 메뉴 · 기본 생활 패턴 추가 (명세 11) */}
      {slotMenu.render({ openActivity, openTaskDetail, requestDeleteTask })}

      {/* AI로 일정 옮기기 — 캘린더 점선 + 오른쪽 패널에서 고치고 적용 (2026-10, 예전 모달) */}
      {aiEdit && (
        <ScheduleAiPanel
          preview={aiEdit.preview}
          ops={aiEdit.ops}
          picked={aiEdit.picked}
          focusIndex={aiEdit.focus}
          today={today}
          applying={aiApply.isPending}
          onChange={(ops, picked) =>
            setAiEdit((s) => (s ? { ...s, ops, picked } : s))
          }
          onApply={applyAiEdit}
          onClose={closeAiEdit}
          onFixOverlaps={() => {
            track("calendar_ai.overlap_fix_click", {
              overlap_count: aiEdit.preview.overlaps.length,
            });
            setAiEdit(null);
            aiBarRef.current?.send("겹치는 루틴 정리해줘");
          }}
        />
      )}

      {add && (
        <TaskAddPanel
          mode={add}
          prefill={add === "manual" ? prefill : undefined}
          onClose={() => {
            setPrefill(undefined);
            go({ add: null });
          }}
          onAdded={(info) => {
            setNewIds((prev) => new Set([...prev, ...info.taskIds]));
            const inView = (d: string) => d >= start && d <= end;
            // AI로 정리한 Task 는 바로 놓지 않고 점선으로 먼저 보여 줘요 → 아래 바에서 "이대로 진행할까요?"
            if (info.pendingTaskIds?.length) {
              const pendingCount = info.pendingTaskIds.length;
              // Esc 로 취소하면 final 이 비어 와서, 받은 제안 수를 따로 기억해요 (task_add_result.proposal_resolve)
              let unscheduledCount = 0;
              const surface = "calendar_panel";
              previewFill(
                { taskIds: info.pendingTaskIds },
                {
                  kind: "new",
                  onPreview: (r) => {
                    unscheduledCount = r.unscheduled.length;
                    track("task_add_result.proposal_ready", {
                      surface,
                      result: "success",
                      placed_count: r.proposals.length,
                      unscheduled_count: r.unscheduled.length,
                    });
                    const first =
                      r.proposals.map((p) => p.date).sort()[0] ?? info.date;
                    if (first && !inView(first)) go({ date: first });
                  },
                  onResolve: (applied, final) => {
                    track("task_add_result.proposal_resolve", {
                      surface,
                      proposal_applied: applied,
                      dragged_count: final.filter(
                        (p) =>
                          p.start !== p.proposedStart ||
                          p.end !== p.proposedEnd,
                      ).length,
                      placed_count: applied ? final.length : 0,
                      unscheduled_count: applied
                        ? unscheduledCount
                        : pendingCount,
                    });
                  },
                  onError: () =>
                    track("task_add_result.proposal_ready", {
                      surface,
                      result: "fail",
                      placed_count: 0,
                      unscheduled_count: pendingCount,
                    }),
                },
              );
              return;
            }
            // 추가한 날이 지금 보는 기간 밖이면 그 주(날)로 옮겨요
            if (info.date && !inView(info.date)) go({ date: info.date });
          }}
        />
      )}
    </div>
  );
}

function Legend() {
  const item = "flex items-center gap-1.5";
  return (
    <ul
      className="text-ink-3 ml-auto hidden flex-wrap items-center gap-x-3 gap-y-1 text-xs md:flex"
      aria-label="범례"
    >
      <li className={item}>
        <span className="border-brand bg-surface size-2.5 rounded-sm border" />
        Task
      </li>
      <li className={item}>
        <span className="border-brand bg-brand-soft size-2.5 rounded-sm border" />
        완료
      </li>
      <li className={item}>
        <Repeat className="size-3" />
        루틴
      </li>
      <li className={item}>
        <span
          className="border-border-subtle size-2.5 rounded-sm border"
          style={HATCH}
        />
        고정 시간
      </li>
      <li className={item}>
        <span className="border-success border-line bg-surface size-2.5 rounded-sm border border-l-2" />
        실제로 한 일
      </li>
    </ul>
  );
}
