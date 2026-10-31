package com.naleul.naleul.domain.work.support;

import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.enums.TaskStatus;
import com.naleul.naleul.domain.work.dto.response.WorkBoardResponse;
import com.naleul.naleul.domain.work.dto.response.WorkTaskItem;
import com.naleul.naleul.domain.work.entity.WorkTopic;
import com.naleul.naleul.domain.work.enums.WorkBoardColumn;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 업무 보드 계산 (V52, DB 없이 순수 계산).
 *
 * 규칙
 *  - 보드 · 개수 · 진행률은 일회성 Task 만 센다. 반복 루틴(주간 회의 등)의 그날 Task 는 "오늘 할 일"에만 보인다
 *    (매주 생기는 루틴 Task 가 보드를 덮지 않게).
 *  - 오늘 할 일 = 안 끝난 일회성 Task 중 (하기로 한 날 ≤ 오늘) 또는 (마감 ≤ 오늘) + 오늘의 루틴 Task.
 *    순서: 마감 지난 것 → 오늘 마감 → 진행 중 → 시간 정해진 순 → 나머지
 *  - 마감 임박 = 오늘 이후 14일 안에 마감인 남은 Task
 */
public final class WorkBoardCalculator {

    static final int UPCOMING_DAYS = 14;
    static final int DONE_COLUMN_LIMIT = 50;
    private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("M/d");

    private WorkBoardCalculator() {
    }

    public static WorkBoardResponse build(GoalCategory goal, List<WorkTopic> topics, List<Task> tasks,
                                          List<WorkBoardResponse.Retro> retros, LocalDate today, int archiveDays) {
        List<Task> oneOffs = tasks.stream().filter(Task::isOneOff).toList();
        List<Task> routineToday = tasks.stream()
                .filter(Task::isRoutineTask)
                .filter(t -> today.equals(t.plannedDate()))
                .toList();

        List<Task> open = oneOffs.stream().filter(t -> !done(t)).toList();
        List<Task> finished = oneOffs.stream().filter(WorkBoardCalculator::done).toList();

        // ── 요약
        int total = oneOffs.size();
        int doneCount = finished.size();
        int inProgress = (int) open.stream().filter(t -> t.boardColumn() == WorkBoardColumn.IN_PROGRESS).count();
        int notStarted = open.size() - inProgress;
        int overdue = (int) open.stream().filter(t -> t.getDueDate() != null && t.getDueDate().isBefore(today)).count();
        int dueThisWeek = (int) open.stream().filter(t -> t.getDueDate() != null
                && !t.getDueDate().isBefore(today) && !t.getDueDate().isAfter(today.plusDays(6))).count();
        int backlogCount = (int) open.stream().filter(t -> t.plannedDate() == null).count();

        LocalDate start = goal.getGoalCategoryStartDate();
        Integer progress = total == 0 ? null : (int) Math.round(doneCount * 100.0 / total);

        // ── 주제 (로드맵) — 마감 순서대로. 안 끝난 주제 중 마감이 가장 가까운(지난 것 포함) 것이 "다음 마감"
        List<TopicCount> counts = new ArrayList<>();
        for (WorkTopic topic : topics) {
            List<Task> in = oneOffs.stream().filter(t -> t.getWorkTopic() != null
                    && Objects.equals(t.getWorkTopic().getWorkTopicId(), topic.getWorkTopicId())).toList();
            int d = (int) in.stream().filter(WorkBoardCalculator::done).count();
            counts.add(new TopicCount(topic, in.size(), d));
        }
        TopicCount next = counts.stream()
                .filter(c -> !c.isDone() && c.topic().getDueDate() != null)
                .min(Comparator.comparing((TopicCount c) -> c.topic().getDueDate()).thenComparing(c -> c.topic().getSortOrder()))
                .orElse(null);
        List<WorkBoardResponse.Topic> timeline = counts.stream()
                .sorted(timelineOrder())
                .map(c -> toTopic(c, c == next, today))
                .toList();
        int topicsDone = (int) counts.stream().filter(TopicCount::isDone).count();

        WorkBoardResponse.Summary summary = new WorkBoardResponse.Summary(start,
                total, notStarted, inProgress, doneCount, open.size(), overdue, dueThisWeek, backlogCount,
                progress, topicsDone, counts.size(), next == null ? null : toTopic(next, true, today));

        // ── 오늘
        List<Task> todayTasks = new ArrayList<>();
        for (Task t : open) {
            LocalDate d = t.plannedDate();
            boolean planned = d != null && !d.isAfter(today);
            boolean due = t.getDueDate() != null && !t.getDueDate().isAfter(today);
            if (planned || due) todayTasks.add(t);
        }
        routineToday.stream().filter(t -> !done(t)).forEach(todayTasks::add);
        todayTasks.sort(todayOrder(today));

        List<WorkTaskItem> doneToday = tasks.stream()
                .filter(WorkBoardCalculator::done)
                .filter(t -> t.getCompletedAt() != null && t.getCompletedAt().toLocalDate().equals(today))
                .sorted(Comparator.comparing(Task::getCompletedAt).reversed())
                .map(t -> WorkTaskItem.from(t, today))
                .toList();

        List<WorkTaskItem> upcoming = open.stream()
                .filter(t -> t.getDueDate() != null && t.getDueDate().isAfter(today)
                        && !t.getDueDate().isAfter(today.plusDays(UPCOMING_DAYS)))
                .sorted(Comparator.comparing(Task::getDueDate).thenComparing(Task::getTaskId))
                .map(t -> WorkTaskItem.from(t, today))
                .toList();

        // ── 보드 필터용 주제 (만든 순서 + 주제 없음)
        List<WorkBoardResponse.Topic> topicRows = new ArrayList<>();
        for (TopicCount c : counts) topicRows.add(toTopic(c, c == next, today));
        List<Task> noTopic = oneOffs.stream().filter(t -> t.getWorkTopic() == null).toList();
        if (!noTopic.isEmpty()) {
            int d = (int) noTopic.stream().filter(WorkBoardCalculator::done).count();
            topicRows.add(new WorkBoardResponse.Topic(null, "세부 목표 없음", null, Integer.MAX_VALUE, null, null,
                    WorkBoardResponse.TopicStatus.NO_DUE, false, noTopic.size(), d, noTopic.size() - d,
                    percent(d, noTopic.size()), null));
        }

        // ── 보드
        Comparator<Task> openOrder = openOrder();
        List<WorkTaskItem> colNotStarted = open.stream().filter(t -> t.boardColumn() == WorkBoardColumn.NOT_STARTED)
                .sorted(openOrder).map(t -> WorkTaskItem.from(t, today)).toList();
        List<WorkTaskItem> colInProgress = open.stream().filter(t -> t.boardColumn() == WorkBoardColumn.IN_PROGRESS)
                .sorted(openOrder).map(t -> WorkTaskItem.from(t, today)).toList();
        List<Task> finishedSorted = finished.stream().sorted(doneOrder()).toList();
        List<WorkTaskItem> colDone = finishedSorted.stream().limit(DONE_COLUMN_LIMIT)
                .map(t -> WorkTaskItem.from(t, today)).toList();
        WorkBoardResponse.Board board = new WorkBoardResponse.Board(colNotStarted, colInProgress, colDone, doneCount);

        List<WorkTaskItem> backlog = open.stream().filter(t -> t.plannedDate() == null)
                .sorted(openOrder).map(t -> WorkTaskItem.from(t, today)).toList();

        // ── 아카이브 (끝낸 날짜별)
        LocalDate archiveFrom = today.minusDays(archiveDays - 1L);
        Map<LocalDate, List<WorkTaskItem>> byDay = new LinkedHashMap<>();
        for (Task t : finishedSorted) {
            LocalDate d = doneDate(t);
            if (d == null || d.isBefore(archiveFrom)) continue;
            byDay.computeIfAbsent(d, k -> new ArrayList<>()).add(WorkTaskItem.from(t, today));
        }
        List<WorkBoardResponse.ArchiveDay> archive = byDay.entrySet().stream()
                .map(e -> new WorkBoardResponse.ArchiveDay(e.getKey(), e.getValue().size(), e.getValue()))
                .toList();

        return new WorkBoardResponse(goal.getGoalCategoryId(), goal.getGoalCategoryName(), goal.getEmoji(),
                goal.getMotive(), today, summary, timeline,
                todayTasks.stream().map(t -> WorkTaskItem.from(t, today)).toList(),
                doneToday, upcoming, topicRows, board, backlog, archive, retros);
    }

    /**
     * 공유 텍스트. scope = TODAY(오늘 할 일만) · REMAINING(남은 일, 기본) · ALL(남은 일 + 최근 완료)
     */
    public static String shareText(WorkBoardResponse b, String scope) {
        String s = scope == null ? "REMAINING" : scope.strip().toUpperCase(Locale.ROOT);
        StringBuilder out = new StringBuilder();
        WorkBoardResponse.Summary sum = b.summary();

        out.append("📌 ").append(b.emoji() != null ? b.emoji() + " " : "").append(b.goalName()).append('\n');
        if (sum.nextTopic() != null && sum.nextTopic().dueDate() != null) {
            WorkBoardResponse.Topic n = sum.nextTopic();
            out.append("다음 마감: ").append(n.name()).append(' ').append(dateLabel(n.dueDate()))
                    .append(" · ").append(dDayLabel(n.dDay())).append('\n');
        }
        if (sum.total() > 0) {
            out.append("진행 ").append(sum.done()).append('/').append(sum.total()).append(" 완료");
            if (sum.progressPercent() != null) out.append(" (").append(sum.progressPercent()).append("%)");
            if (sum.overdue() > 0) out.append(" · 마감 지남 ").append(sum.overdue()).append("개");
            out.append('\n');
        }
        // 로드맵 — 주제를 마감 순서대로
        if (!b.timeline().isEmpty()) {
            out.append("\n[로드맵]\n");
            for (WorkBoardResponse.Topic t : b.timeline()) out.append(roadmapLine(t)).append('\n');
        }

        if (s.equals("TODAY")) {
            section(out, "오늘 할 일 (" + dateLabel(b.today()) + ")", b.todayTasks(), false);
            section(out, "오늘 끝낸 일", b.doneToday(), true);
            return out.toString().strip();
        }

        section(out, "진행 중", b.board().inProgress(), false);
        // 시작 전은 주제별로 묶는다
        Map<String, List<WorkTaskItem>> byTopic = new LinkedHashMap<>();
        for (WorkBoardResponse.Topic t : b.topics()) byTopic.put(topicKey(t.topicId(), t.name()), new ArrayList<>());
        for (WorkTaskItem item : b.board().notStarted()) {
            String key = topicKey(item.topicId(), item.topicName() != null ? item.topicName() : "세부 목표 없음");
            byTopic.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
        }
        boolean header = false;
        for (Map.Entry<String, List<WorkTaskItem>> e : byTopic.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            if (!header) {
                out.append("\n[시작 전]\n");
                header = true;
            }
            boolean onlyNoTopic = byTopic.values().stream().filter(l -> !l.isEmpty()).count() == 1
                    && e.getKey().startsWith("null:");
            if (!onlyNoTopic) out.append("· ").append(e.getKey().substring(e.getKey().indexOf(':') + 1)).append('\n');
            for (WorkTaskItem item : e.getValue()) out.append(line(item, false)).append('\n');
        }
        if (s.equals("ALL")) {
            List<WorkTaskItem> recent = b.archive().stream().flatMap(d -> d.tasks().stream()).toList();
            section(out, "최근 완료 (7일)", recent, true);
        }
        return out.toString().strip();
    }

    // ── 내부 ─────────────────────────────────────────────

    private static void section(StringBuilder out, String title, List<WorkTaskItem> items, boolean done) {
        if (items == null || items.isEmpty()) return;
        out.append('\n').append('[').append(title).append("]\n");
        for (WorkTaskItem item : items) out.append(line(item, done)).append('\n');
    }

    private static String line(WorkTaskItem t, boolean done) {
        StringBuilder sb = new StringBuilder(done || t.status() == WorkBoardColumn.DONE ? "☑ " : "☐ ");
        sb.append(t.taskName());
        List<String> tags = new ArrayList<>();
        if (t.dueDate() != null) {
            tags.add(t.overdue() ? "마감 " + MD.format(t.dueDate()) + " 지남" : "~" + MD.format(t.dueDate()));
        }
        if (t.memo() != null) tags.add(t.memo().length() > 40 ? t.memo().substring(0, 40) + "…" : t.memo());
        if (!tags.isEmpty()) sb.append(" (").append(String.join(" · ", tags)).append(')');
        return sb.toString();
    }

    private static String roadmapLine(WorkBoardResponse.Topic t) {
        String mark = switch (t.status()) {
            case DONE -> "✅";
            case OVERDUE -> "⚠️";
            case NEXT -> "▶";
            default -> "○";
        };
        StringBuilder sb = new StringBuilder(mark).append(' ').append(t.name());
        List<String> tags = new ArrayList<>();
        if (t.dueDate() != null) tags.add((t.status() == WorkBoardResponse.TopicStatus.DONE ? "" : "~") + MD.format(t.dueDate()));
        if (t.status() == WorkBoardResponse.TopicStatus.NEXT || t.status() == WorkBoardResponse.TopicStatus.OVERDUE) {
            if (t.dDay() != null) tags.add(dDayLabel(t.dDay()));
        }
        if (t.status() != WorkBoardResponse.TopicStatus.DONE && t.remaining() > 0) tags.add("남은 " + t.remaining() + "개");
        if (!tags.isEmpty()) sb.append(" (").append(String.join(" · ", tags)).append(')');
        return sb.toString();
    }

    /** 주제 하나의 Task 개수 (계산 중간값) */
    record TopicCount(WorkTopic topic, int total, int done) {
        /** 직접 끝냈다고 표시했거나, Task 가 있고 모두 끝남 */
        boolean isDone() {
            return topic.isMarkedCompleted() || (total > 0 && done == total);
        }
    }

    /** 로드맵 순서: 마감 있는 주제를 마감 순 → 마감 없는 주제는 만든 순서 */
    static Comparator<TopicCount> timelineOrder() {
        return Comparator
                .comparing((TopicCount c) -> c.topic().getDueDate() == null ? 1 : 0)
                .thenComparing(c -> c.topic().getDueDate() == null ? LocalDate.MAX : c.topic().getDueDate())
                .thenComparing(c -> c.topic().getSortOrder())
                .thenComparing(c -> c.topic().getWorkTopicId(), Comparator.nullsLast(Comparator.naturalOrder()));
    }

    static WorkBoardResponse.Topic toTopic(TopicCount c, boolean isNext, LocalDate today) {
        WorkTopic t = c.topic();
        LocalDate due = t.getDueDate();
        Integer dDay = due == null ? null : (int) ChronoUnit.DAYS.between(today, due);
        int remaining = c.total() - c.done();
        WorkBoardResponse.TopicStatus status = c.isDone() ? WorkBoardResponse.TopicStatus.DONE
                : due == null ? WorkBoardResponse.TopicStatus.NO_DUE
                : dDay < 0 ? WorkBoardResponse.TopicStatus.OVERDUE
                : isNext ? WorkBoardResponse.TopicStatus.NEXT
                : WorkBoardResponse.TopicStatus.UPCOMING;
        Double perDay = c.isDone() || dDay == null || dDay < 0 || remaining == 0 ? null
                : Math.round(remaining * 10.0 / (dDay + 1)) / 10.0;
        return new WorkBoardResponse.Topic(t.getWorkTopicId(), t.getTopicName(), t.getEmoji(), t.getSortOrder(),
                due, dDay, status, t.isMarkedCompleted(), c.total(), c.done(), remaining,
                percent(c.done(), c.total()), perDay);
    }

    private static Integer percent(int done, int total) {
        return total == 0 ? null : (int) Math.round(done * 100.0 / total);
    }

    private static String topicKey(Long id, String name) {
        return id + ":" + name;
    }

    static boolean done(Task t) {
        return t.getTaskStatus() == TaskStatus.COMPLETED;
    }

    /** 끝낸 날: 완료 시각 → 없으면 하기로 한 날 */
    static LocalDate doneDate(Task t) {
        LocalDateTime at = t.getCompletedAt();
        return at != null ? at.toLocalDate() : t.plannedDate();
    }

    /** 오늘 할 일 순서 */
    static Comparator<Task> todayOrder(LocalDate today) {
        return Comparator
                .comparing((Task t) -> rank(t, today))
                .thenComparing(t -> t.getPlannedStartAt() == null ? LocalTime.MAX : t.getPlannedStartAt().toLocalTime())
                .thenComparing(t -> t.getDueDate() == null ? LocalDate.MAX : t.getDueDate())
                .thenComparing(Task::getTaskId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static int rank(Task t, LocalDate today) {
        if (t.getDueDate() != null && t.getDueDate().isBefore(today)) return 0;   // 마감 지남
        if (today.equals(t.getDueDate())) return 1;                               // 오늘 마감
        if (t.boardColumn() == WorkBoardColumn.IN_PROGRESS) return 2;             // 하던 것
        if (t.getPlannedStartAt() != null) return 3;                              // 시간 정해 둔 것
        return 4;
    }

    /** 보드의 남은 칸 · 할 일 목록 순서: 마감 가까운 순 → 하기로 한 날 → 만든 순 */
    static Comparator<Task> openOrder() {
        return Comparator
                .comparing((Task t) -> t.getDueDate() == null ? LocalDate.MAX : t.getDueDate())
                .thenComparing(t -> t.plannedDate() == null ? LocalDate.MAX : t.plannedDate())
                .thenComparing(Task::getTaskId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    /** 완료 칸 · 아카이브 순서: 최근에 끝낸 것 먼저 */
    static Comparator<Task> doneOrder() {
        return Comparator
                .comparing((Task t) -> t.getCompletedAt() == null
                        ? (t.plannedDate() == null ? LocalDateTime.MIN : t.plannedDate().atStartOfDay())
                        : t.getCompletedAt())
                .reversed()
                .thenComparing(Task::getTaskId, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    static String dateLabel(LocalDate d) {
        return d.getMonthValue() + "/" + d.getDayOfMonth() + "(" + d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN) + ")";
    }

    static String dDayLabel(Integer dDay) {
        if (dDay == null) return "";
        if (dDay == 0) return "D-Day";
        return dDay > 0 ? "D-" + dDay : "D+" + (-dDay);
    }
}
