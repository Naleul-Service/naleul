package com.naleul.naleul.domain.home.support;

import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus;
import com.naleul.naleul.domain.goalCategory.enums.GoalMode;
import com.naleul.naleul.domain.goalProgress.enums.ReflectionMood;
import com.naleul.naleul.domain.home.dto.response.HomeTodayResponse;
import com.naleul.naleul.domain.home.dto.response.HomeTodayResponse.Deadline;
import com.naleul.naleul.domain.home.dto.response.HomeTodayResponse.DeadlineType;
import com.naleul.naleul.domain.home.dto.response.HomeTodayResponse.GoalToday;
import com.naleul.naleul.domain.retrospective.entity.Retrospective;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.enums.TaskStatus;
import com.naleul.naleul.domain.work.dto.response.WorkBoardResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 홈 "오늘" 계산 (DB 없이 테스트 가능). 데이터 모으기는 HomeService.
 */
public final class HomeCalculator {

    /** 마감이 지난 것은 최근 7일 것까지만 (오래 묵은 건 각 목표 화면에서) */
    public static final int OVERDUE_DAYS = 7;
    /** Task 마감은 앞으로 14일 */
    public static final int TASK_DAYS = 14;
    /**
     * 업무형 주제 마감 · 목표형 종료일은 앞으로 1년까지 — 홈의 "마감 로드맵"은 큰 단계를 다 보여 주는 게 목적이라
     * (예전엔 30일이라 종료일이 한 달 넘게 남은 목표형이 안 보였다)
     */
    public static final int LONG_DAYS = 365;
    static final int MAX_NAMES = 5;

    private HomeCalculator() {
    }

    /** 업무형 주제 하나와 그 주제가 속한 목표 */
    public record TopicDeadline(GoalCategory goal, WorkBoardResponse.Topic topic) {
    }

    /** 목표형 그날 기록 (수치 · 회고) — goalCategoryId 로 찾아요 */
    public record DayRecord(BigDecimal value, ReflectionMood mood, String note) {
    }

    public static HomeTodayResponse build(LocalDate today, List<GoalCategory> goals, List<Task> dueTasks,
                                          List<TopicDeadline> topics, List<Task> todayTasks,
                                          List<Retrospective> todayRetros) {
        return build(today, goals, dueTasks, topics, todayTasks, todayRetros, Map.of());
    }

    public static HomeTodayResponse build(LocalDate today, List<GoalCategory> goals, List<Task> dueTasks,
                                          List<TopicDeadline> topics, List<Task> todayTasks,
                                          List<Retrospective> todayRetros, Map<Long, DayRecord> records) {
        return new HomeTodayResponse(today, deadlines(today, goals, dueTasks, topics),
                goalsToday(today, goals, todayTasks, todayRetros, records));
    }

    // ── 다가오는 마감 ─────────────────────────────────────

    static List<Deadline> deadlines(LocalDate today, List<GoalCategory> goals, List<Task> dueTasks,
                                    List<TopicDeadline> topics) {
        LocalDate from = today.minusDays(OVERDUE_DAYS);
        List<Deadline> list = new ArrayList<>();

        for (Task t : dueTasks) {
            LocalDate due = t.getDueDate();
            if (due == null || due.isBefore(from) || due.isAfter(today.plusDays(TASK_DAYS))) continue;
            if (t.getTaskStatus() != TaskStatus.TODO || t.getRoutine() != null) continue;
            GoalCategory g = t.getGoalCategory();
            if (g != null && !isLive(g)) continue;
            list.add(deadline(DeadlineType.TASK, t.getTaskId(), t.getTaskName(), t.getEmoji(), due, today, g, null));
        }

        for (TopicDeadline td : topics) {
            WorkBoardResponse.Topic t = td.topic();
            if (t.topicId() == null || t.dueDate() == null || t.status() == WorkBoardResponse.TopicStatus.DONE) continue;
            if (t.dueDate().isBefore(from) || t.dueDate().isAfter(today.plusDays(LONG_DAYS))) continue;
            list.add(deadline(DeadlineType.TOPIC, t.topicId(), t.name(), t.emoji(), t.dueDate(), today, td.goal(),
                    t.remaining()));
        }

        for (GoalCategory g : goals) {
            LocalDate end = g.getGoalCategoryEndDate();
            // 진행 중 + 시작 전(시작일이 미래) 목표형 — 종료일이 있는 것만
            if (!g.isAchievement() || g.isTemporary() || !isLive(g)) continue;
            if (end == null || end.isBefore(today) || end.isAfter(today.plusDays(LONG_DAYS))) continue;
            list.add(deadline(DeadlineType.GOAL, g.getGoalCategoryId(), g.getGoalCategoryName(), g.getEmoji(), end,
                    today, g, null));
        }

        list.sort(Comparator.comparing(Deadline::date)
                .thenComparing(d -> d.type().ordinal())
                .thenComparing(Deadline::id));
        return list;
    }

    private static Deadline deadline(DeadlineType type, Long id, String title, String emoji, LocalDate date,
                                     LocalDate today, GoalCategory g, Integer remaining) {
        int dDay = (int) ChronoUnit.DAYS.between(today, date);
        return new Deadline(type, id, title, emoji, date, dDay, dDay < 0,
                g != null ? g.getGoalCategoryId() : null,
                g != null ? g.getGoalCategoryName() : null,
                g != null ? g.getEmoji() : null,
                colorOf(g),
                g != null && g.getGoalMode() != null ? g.getGoalMode().name() : null,
                remaining);
    }

    // ── 목표별 오늘 ──────────────────────────────────────

    static List<GoalToday> goalsToday(LocalDate today, List<GoalCategory> goals, List<Task> todayTasks,
                                      List<Retrospective> todayRetros) {
        return goalsToday(today, goals, todayTasks, todayRetros, Map.of());
    }

    static List<GoalToday> goalsToday(LocalDate today, List<GoalCategory> goals, List<Task> todayTasks,
                                      List<Retrospective> todayRetros, Map<Long, DayRecord> records) {
        Map<Long, List<Task>> tasksByGoal = new HashMap<>();
        for (Task t : todayTasks) {
            if (t.getGoalCategory() == null) continue;
            tasksByGoal.computeIfAbsent(t.getGoalCategory().getGoalCategoryId(), k -> new ArrayList<>()).add(t);
        }
        Map<Long, List<HomeTodayResponse.Retro>> retrosByGoal = new HashMap<>();
        for (Retrospective r : todayRetros) {
            if (r.getGoalCategory() == null || !today.equals(r.getReviewDate())) continue;
            retrosByGoal.computeIfAbsent(r.getGoalCategory().getGoalCategoryId(), k -> new ArrayList<>())
                    .add(new HomeTodayResponse.Retro(r.getRetrospectiveId(),
                            r.getReviewType() != null ? r.getReviewType().name() : null, r.getContent()));
        }

        List<GoalToday> list = new ArrayList<>();
        for (GoalCategory g : goals) {
            if (g.isHabit() || g.isTemporary() || g.getGoalCategoryStatus() != GoalCategoryStatus.IN_PROGRESS) continue;
            List<Task> ts = tasksByGoal.getOrDefault(g.getGoalCategoryId(), List.of());
            List<HomeTodayResponse.Retro> retros = retrosByGoal.getOrDefault(g.getGoalCategoryId(), List.of());

            int planned = 0;
            List<String> doneNames = new ArrayList<>();
            List<String> openNames = new ArrayList<>();
            int done = 0;
            for (Task t : ts) {
                boolean scheduled = scheduledOn(t, today);
                boolean completed = t.getTaskStatus() == TaskStatus.COMPLETED;
                boolean doneToday = completed && (scheduled
                        || (t.getCompletedAt() != null && today.equals(t.getCompletedAt().toLocalDate())));
                if (scheduled) planned++;
                if (doneToday) {
                    done++;
                    if (doneNames.size() < MAX_NAMES) doneNames.add(t.getTaskName());
                } else if (scheduled && !completed && openNames.size() < MAX_NAMES) {
                    openNames.add(t.getTaskName());
                }
            }
            HomeTodayResponse.CheckIn checkIn = checkInOf(g, records.get(g.getGoalCategoryId()));
            boolean active = planned > 0 || done > 0 || !retros.isEmpty() || (checkIn != null && checkIn.written());
            list.add(new GoalToday(g.getGoalCategoryId(), g.getGoalCategoryName(), g.getEmoji(), colorOf(g),
                    g.getGoalMode() != null ? g.getGoalMode().name() : null, active, planned, done,
                    doneNames, openNames, retros, checkIn));
        }
        // 오늘 할 일 · 회고가 있는 목표 먼저, 그 안에서는 끝낸 게 많은 순 → 목표 id
        list.sort(Comparator.comparing((GoalToday x) -> !x.activeToday())
                .thenComparing(x -> -x.done())
                .thenComparing(x -> -x.planned())
                .thenComparing(GoalToday::goalCategoryId));
        return list;
    }

    /** 목표형만 "오늘 기록"을 홈에서 써요 (업무형은 지금처럼 회고 글) */
    static HomeTodayResponse.CheckIn checkInOf(GoalCategory g, DayRecord r) {
        if (g.getGoalMode() != GoalMode.ACHIEVEMENT) return null;
        boolean hasMetric = g.getTargetValue() != null && g.getStartValue() != null;
        return new HomeTodayResponse.CheckIn(hasMetric,
                hasMetric ? g.getMetricName() : null,
                hasMetric ? g.getMetricUnit() : null,
                hasMetric ? g.getCurrentValue() : null,
                r != null && hasMetric ? r.value() : null,
                r != null && r.mood() != null ? r.mood().name() : null,
                r != null ? r.note() : null);
    }

    /** 오늘로 잡혀 있는지 — 시작 시각이 오늘이거나, 시간 미정이고 날짜가 오늘 */
    static boolean scheduledOn(Task t, LocalDate day) {
        if (t.getPlannedStartAt() != null) return day.equals(t.getPlannedStartAt().toLocalDate());
        return day.equals(t.getRoutineDate());
    }

    private static boolean isLive(GoalCategory g) {
        return g.getGoalCategoryStatus() == GoalCategoryStatus.IN_PROGRESS
                || g.getGoalCategoryStatus() == GoalCategoryStatus.NOT_STARTED;
    }

    private static String colorOf(GoalCategory g) {
        return g != null && g.getUserColor() != null ? g.getUserColor().getColorCode() : null;
    }
}
