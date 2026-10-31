"use client";

import { useEffect, useRef } from "react";
import { AlertTriangle } from "lucide-react";
import { Repeat, X } from "@/components/icons";
import { Button } from "@/components/ui/Button";
import { iconButtonClass } from "@/components/ui/IconButton";
import { Chip } from "@/components/ui/Chip";
import { AIBadge } from "@/components/ui/AIBadge";
import { cn } from "@/lib/cn";
import { track } from "@/lib/client/analytics";
import { goalAccent } from "@/lib/goalTone";
import {
  addDays,
  formatDuration,
  minutesFrom,
  minutesToTime,
  timeToMinutes,
  toDateTime,
  weekdayIndex,
} from "../time";
import type { TimeBlockTask, TimetableDay } from "../types";
import type { ScheduleEditOp, ScheduleEditPreview } from "./ScheduleAiBar";

const DAY_CODES = ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"] as const;
const DAY_LABEL = ["월", "화", "수", "목", "금", "토", "일"];
const field =
  "border-border-subtle bg-surface focus:border-border-focus h-9 min-w-0 rounded-lg border px-2.5 text-[13px] outline-none";

/** "HH:mm:ss" | "HH:mm" → "HH:mm" */
const hhmm = (t: string | null | undefined) => (t ? t.slice(0, 5) : "");

/**
 * AI로 일정 옮기기 미리보기를 캘린더 블록에 입혀요 (2026-10).
 * 고른 변경만 · 패널에서 고친 값 그대로 — 옮겨질 Task 를 새 자리의 점선 "AI 제안" 블록으로 바꿔 그려요 (끌 수 없음).
 *  - MOVE_TASK: 그 Task 하나를 date · startTime 으로
 *  - MOVE_ROUTINE: 보고 있는 기간의 그 루틴 Task (오늘 이후 · 안 끝낸 것) 를 새 시각으로. 요일에서 빠지면 그날 블록을 숨겨요
 */
export function applyAiEditPreview(
  days: TimetableDay[],
  ops: ScheduleEditOp[],
  picked: boolean[],
  today: string,
): TimetableDay[] {
  const active = ops.filter((_, i) => picked[i]);
  if (!active.length) return days;
  const taskOps = new Map(
    active.filter((o) => o.type === "MOVE_TASK").map((o) => [o.targetId, o]),
  );
  const routineOps = new Map(
    active.filter((o) => o.type === "MOVE_ROUTINE").map((o) => [o.targetId, o]),
  );
  const reasonOf = (o: ScheduleEditOp) => o.reason ?? "AI로 일정 옮기기 제안";
  const preview = (
    t: TimeBlockTask,
    date: string,
    startMin: number,
    minutes: number,
    o: ScheduleEditOp,
  ) => ({
    ...t,
    date,
    plannedStartAt: toDateTime(date, startMin),
    plannedEndAt: toDateTime(date, startMin + minutes),
    plannedDurationMinutes: minutes,
    plannedSegments: [],
    proposed: true,
    placementReason: reasonOf(o),
  });
  const lengthOf = (t: TimeBlockTask) =>
    t.plannedStartAt && t.plannedEndAt
      ? minutesFrom(t.plannedStartAt.slice(0, 10), t.plannedEndAt) -
        minutesFrom(t.plannedStartAt.slice(0, 10), t.plannedStartAt)
      : (t.plannedDurationMinutes ?? 30);

  const moved: { date: string; task: TimeBlockTask }[] = [];
  const next = days.map((d) => {
    const tasks: TimeBlockTask[] = [];
    for (const t of d.tasks) {
      const to = taskOps.get(t.taskId);
      if (to && to.startTime) {
        const date = to.date ?? d.date;
        moved.push({
          date,
          task: preview(
            t,
            date,
            timeToMinutes(hhmm(to.startTime)),
            to.durationMinutes ?? lengthOf(t),
            to,
          ),
        });
        continue;
      }
      const ro = t.routineId != null ? routineOps.get(t.routineId) : undefined;
      if (
        ro &&
        t.taskStatus === "TODO" &&
        d.date >= today &&
        t.plannedStartAt
      ) {
        if (ro.days && !ro.days.includes(DAY_CODES[weekdayIndex(d.date)]))
          continue; // 이 요일에서 빠져요
        const start = ro.startTime
          ? timeToMinutes(hhmm(ro.startTime))
          : minutesFrom(d.date, t.plannedStartAt);
        tasks.push(
          preview(t, d.date, start, ro.durationMinutes ?? lengthOf(t), ro),
        );
        continue;
      }
      tasks.push(t);
    }
    return { ...d, tasks };
  });
  // 다른 날로 옮기는 Task 는 그 날 칸에 (보고 있는 기간 밖이면 패널에서만 보여요)
  return next.map((d) => ({
    ...d,
    tasks: [
      ...d.tasks,
      ...moved.filter((m) => m.date === d.date).map((m) => m.task),
    ],
  }));
}

/**
 * AI로 일정 옮기기 — 오른쪽 패널 (2026-10, 예전 "이렇게 옮길까요?" 모달)
 *  - 캘린더에는 점선 "AI 제안"으로 새 자리를 보여주고, 여기서 항목마다 이유를 보고 시간 · 날짜 · 요일 · 길이를 고치거나 빼요
 *  - 고친 값 그대로 [N개 적용] (그대로 둔 항목 · 지금과 같아진 항목은 서버가 건너뛰어요)
 */
export function ScheduleAiPanel({
  preview,
  ops,
  picked,
  focusIndex,
  today,
  applying,
  onChange,
  onApply,
  onClose,
  onFixOverlaps,
}: {
  preview: ScheduleEditPreview;
  ops: ScheduleEditOp[];
  picked: boolean[];
  /** 캘린더에서 누른 점선 블록의 항목 — 그 카드로 스크롤 */
  focusIndex: number | null;
  today: string;
  applying: boolean;
  onChange: (ops: ScheduleEditOp[], picked: boolean[]) => void;
  onApply: () => void;
  onClose: (method: "x_button" | "cancel_button" | "esc") => void;
  onFixOverlaps: () => void;
}) {
  const listRef = useRef<HTMLUListElement>(null);
  const chosen = picked.filter(Boolean).length;

  useEffect(() => {
    if (focusIndex == null) return;
    listRef.current
      ?.querySelector(`[data-change="${focusIndex}"]`)
      ?.scrollIntoView({ behavior: "smooth", block: "nearest" });
  }, [focusIndex]);

  // Esc 로 닫기 (입력 중이면 그 칸이 먼저)
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== "Escape" || e.defaultPrevented || applying) return;
      const el = e.target instanceof HTMLElement ? e.target : null;
      if (el?.closest('input, textarea, select, [role="dialog"]')) return;
      onClose("esc");
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [onClose, applying]);

  const setOp = (
    i: number,
    patch: Partial<ScheduleEditOp>,
    editField: string,
  ) => {
    track("calendar_ai.item_edit", {
      change_kind: preview.changes[i]?.kind,
      edit_field: editField,
    });
    onChange(
      ops.map((o, j) => (j === i ? { ...o, ...patch } : o)),
      picked.map((p, j) => (j === i ? true : p)),
    );
  };
  const toggle = (i: number, checked: boolean) => {
    track("calendar_ai.item_toggle", {
      change_kind: preview.changes[i]?.kind,
      checked,
    });
    onChange(
      ops,
      picked.map((p, j) => (j === i ? checked : p)),
    );
  };

  return (
    <aside
      aria-label="AI로 일정 옮기기"
      className="bg-bg-surface border-border-subtle shadow-pop fixed inset-x-0 bottom-0 z-40 flex max-h-[70dvh] animate-[panel-in_320ms_cubic-bezier(0.22,1,0.36,1)] flex-col rounded-t-2xl border-t sm:inset-y-0 sm:right-0 sm:left-auto sm:max-h-none sm:w-[420px] sm:rounded-none sm:border-t-0 sm:border-l xl:shadow-none"
    >
      <div className="flex shrink-0 items-start gap-2 px-6 pt-6 pb-1">
        <AIBadge />
        <div className="min-w-0 flex-1">
          <h2 className="text-h3 text-text-primary">이렇게 옮길까요?</h2>
          <p className="text-ink-3 mt-1 text-[13px] leading-relaxed">
            {preview.summary}
          </p>
        </div>
        <button
          type="button"
          onClick={() => onClose("x_button")}
          aria-label="AI로 일정 옮기기 닫기"
          className={iconButtonClass("md")}
          disabled={applying}
        >
          <X className="size-[18px]" />
        </button>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto px-6 pt-4 pb-4">
        {preview.changes.length > 0 && (
          <>
            <p className="text-ink-3 mb-3 text-[12px]">
              아직 아무것도 바뀌지 않았어요. 캘린더의 점선이 새 자리예요 — 점선
              블록을 끌어 옮기거나 여기서 시간을 고치고 적용해 주세요.
            </p>
            <ul ref={listRef} className="space-y-2.5">
              {preview.changes.map((c, i) => {
                const o = ops[i];
                const on = !!picked[i];
                return (
                  <li
                    key={i}
                    data-change={i}
                    className={cn(
                      "rounded-2xl border p-3 transition-colors",
                      on
                        ? "border-text-brand/40 bg-surface"
                        : "border-line bg-bg-sunken opacity-60",
                      focusIndex === i && "ring-text-brand ring-2",
                    )}
                  >
                    <label className="flex cursor-pointer items-start gap-2.5">
                      <input
                        type="checkbox"
                        checked={on}
                        onChange={(e) => toggle(i, e.target.checked)}
                        className="mt-1 size-4 shrink-0"
                        aria-label={`${c.target} 적용`}
                      />
                      <span className="min-w-0 flex-1">
                        <span className="flex flex-wrap items-center gap-1.5">
                          {c.kind === "ROUTINE" && (
                            <Repeat
                              className="text-text-brand size-3.5"
                              aria-label="루틴"
                            />
                          )}
                          <span className="text-[14px] font-semibold">
                            {c.target}
                          </span>
                          {c.goal && (
                            <span className="text-ink-3 flex items-center gap-1 text-[12px]">
                              <span
                                className="size-2 rounded-full"
                                style={{
                                  backgroundColor: goalAccent(c.colorCode),
                                }}
                                aria-hidden
                              />
                              {c.goal}
                            </span>
                          )}
                        </span>
                        <span className="text-ink-3 mt-1 block text-[12px] line-through decoration-1">
                          {c.before}
                        </span>
                        <span className="text-ink mt-0.5 block text-[13px]">
                          → {c.after}
                        </span>
                      </span>
                    </label>
                    {o?.reason && (
                      <p className="ai-border-flat text-ink-2 mt-2 rounded-xl px-2.5 py-1.5 text-[12px] leading-relaxed">
                        {o.reason}
                      </p>
                    )}
                    {o && (
                      <div className="mt-2.5 grid grid-cols-2 gap-1.5">
                        {o.type === "MOVE_TASK" ? (
                          <label className="text-ink-3 col-span-2 flex items-center gap-2 text-[12px]">
                            <span className="w-8 shrink-0">날짜</span>
                            <input
                              type="date"
                              min={today}
                              max={addDays(today, 60)}
                              value={o.date ?? ""}
                              onChange={(e) =>
                                setOp(
                                  i,
                                  { date: e.target.value || null },
                                  "date",
                                )
                              }
                              className={cn(field, "flex-1")}
                              aria-label="날짜"
                            />
                          </label>
                        ) : (
                          <div className="col-span-2 flex flex-wrap items-center gap-1">
                            <span className="text-ink-3 w-8 shrink-0 text-[12px]">
                              요일
                            </span>
                            {o.days ? (
                              DAY_CODES.map((d, k) => (
                                <Chip
                                  key={d}
                                  size="sm"
                                  variant="day"
                                  selected={o.days!.includes(d)}
                                  onClick={() => {
                                    const nextDays = o.days!.includes(d)
                                      ? o.days!.filter((x) => x !== d)
                                      : [...o.days!, d];
                                    if (nextDays.length)
                                      setOp(i, { days: nextDays }, "days");
                                  }}
                                  className="w-8 px-0"
                                >
                                  {DAY_LABEL[k]}
                                </Chip>
                              ))
                            ) : (
                              <span className="text-ink-3 text-[12px]">
                                그대로
                              </span>
                            )}
                          </div>
                        )}
                        <label className="text-ink-3 flex items-center gap-2 text-[12px]">
                          <span className="w-8 shrink-0">시작</span>
                          <input
                            type="time"
                            step={600}
                            value={hhmm(o.startTime)}
                            onChange={(e) =>
                              setOp(
                                i,
                                {
                                  startTime:
                                    e.target.value ||
                                    (o.type === "MOVE_TASK"
                                      ? o.startTime
                                      : null),
                                },
                                "start",
                              )
                            }
                            className={cn(field, "flex-1")}
                            aria-label="시작 시각"
                          />
                        </label>
                        <label className="text-ink-3 flex items-center gap-2 text-[12px]">
                          <span className="shrink-0">길이</span>
                          <select
                            value={o.durationMinutes ?? ""}
                            onChange={(e) =>
                              setOp(
                                i,
                                {
                                  durationMinutes: e.target.value
                                    ? Number(e.target.value)
                                    : null,
                                },
                                "duration",
                              )
                            }
                            className={cn(field, "flex-1")}
                            aria-label="길이"
                          >
                            <option value="">그대로</option>
                            {[
                              ...new Set([
                                10,
                                15,
                                20,
                                30,
                                40,
                                45,
                                60,
                                90,
                                120,
                                150,
                                180,
                                240,
                                ...(o.durationMinutes
                                  ? [o.durationMinutes]
                                  : []),
                              ]),
                            ]
                              .sort((a, b) => a - b)
                              .map((m) => (
                                <option key={m} value={m}>
                                  {formatDuration(m)}
                                </option>
                              ))}
                          </select>
                        </label>
                        {o.startTime && o.durationMinutes && (
                          <p className="text-ink-4 col-span-2 text-[11px] tabular-nums">
                            {hhmm(o.startTime)} –{" "}
                            {minutesToTime(
                              (timeToMinutes(hhmm(o.startTime)) +
                                o.durationMinutes) %
                                1440,
                            )}
                          </p>
                        )}
                      </div>
                    )}
                  </li>
                );
              })}
            </ul>
          </>
        )}
        {preview.overlaps.length > 0 && (
          <div className="bg-warning-soft mt-4 rounded-xl px-3 py-2.5 text-[13px]">
            <p className="flex items-center gap-1.5 font-semibold">
              <AlertTriangle className="text-warning size-4" />
              지금 겹쳐 있는 루틴 {preview.overlaps.length}
            </p>
            <ul className="text-ink-2 mt-1 space-y-0.5 pl-6">
              {preview.overlaps.map((o) => (
                <li key={o}>{o}</li>
              ))}
            </ul>
            {preview.changes.length === 0 && (
              <button
                type="button"
                onClick={onFixOverlaps}
                className="text-text-brand mt-1.5 pl-6 text-[12px] font-semibold hover:underline"
              >
                겹치는 루틴 정리해 달라고 하기
              </button>
            )}
          </div>
        )}
      </div>

      <div className="border-border-subtle flex shrink-0 items-center justify-end gap-2 border-t px-6 py-4 pb-[max(16px,env(safe-area-inset-bottom))]">
        {preview.changes.length ? (
          <>
            <Button
              variant="secondary"
              onClick={() => onClose("cancel_button")}
              disabled={applying}
            >
              취소
            </Button>
            <Button onClick={onApply} loading={applying} disabled={!chosen}>
              {chosen}개 적용
            </Button>
          </>
        ) : (
          <Button variant="secondary" onClick={() => onClose("cancel_button")}>
            다시 적기
          </Button>
        )}
      </div>
    </aside>
  );
}
