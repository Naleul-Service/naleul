package com.naleul.naleul.domain.pattern.calculator;

import com.naleul.naleul.domain.lifePattern.enums.LifePatternType;
import com.naleul.naleul.domain.pattern.entity.BrainDumpLog;
import com.naleul.naleul.domain.pattern.entity.TaskScheduleLog;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** 계산기 테스트용 데이터 만들기 */
class PatternFixtures {

    /** 2026-11-02(월) ~ 2026-11-29(일), 28일 = 정확히 4주 */
    static final LocalDate START = LocalDate.of(2026, 11, 2);
    static final LocalDate END = LocalDate.of(2026, 11, 29);
    static final PatternRange RANGE = new PatternRange(START, END);

    private long seq = 1;
    final List<PatternTaskSample> tasks = new ArrayList<>();
    final List<CarryMissSample> carries = new ArrayList<>();
    final List<TaskScheduleLog> logs = new ArrayList<>();
    final List<BrainDumpLog> brainDumps = new ArrayList<>();
    final List<LifePatternSample> patterns = new ArrayList<>();
    int legacy = 0;

    PatternSampleSet set() {
        return new PatternSampleSet(RANGE, tasks, carries, legacy, logs, brainDumps, patterns);
    }

    TaskBuilder task() {
        return new TaskBuilder(seq++);
    }

    /** 날짜 d 의 h 시 시작 30분 블록 */
    PatternTaskSample block(LocalDate d, int h, boolean done) {
        return task().at(d.atTime(h, 0)).done(done).add();
    }

    void custom(String title, int sh, int sm, int eh, int em, int mask) {
        patterns.add(new LifePatternSample(title, LifePatternType.CUSTOM, LocalTime.of(sh, sm), LocalTime.of(eh, em), mask));
    }

    class TaskBuilder {
        private final long id;
        private Long routineId;
        private String routineName;
        private Long goalId = 1L;
        private String goalName = "토익 900점";
        private boolean temporary;
        private LocalDate goalStart = START.minusDays(30);
        private String name = "task";
        private boolean done;
        private LocalDateTime start;
        private int minutes = 30;
        private LocalDate date = START;
        private LocalDate due;
        private LocalDateTime completedAt;
        private int carry;
        private LocalDateTime actualStart;
        private Integer actualMinutes;

        TaskBuilder(long id) {
            this.id = id;
        }

        TaskBuilder routine(long rid, String rname) {
            this.routineId = rid;
            this.routineName = rname;
            return this;
        }

        TaskBuilder goal(long gid, String gname) {
            this.goalId = gid;
            this.goalName = gname;
            return this;
        }

        TaskBuilder temporary(LocalDate goalStartDate) {
            this.temporary = true;
            this.goalStart = goalStartDate;
            return this;
        }

        TaskBuilder name(String n) {
            this.name = n;
            return this;
        }

        TaskBuilder at(LocalDateTime s) {
            this.start = s;
            this.date = s.toLocalDate();
            return this;
        }

        TaskBuilder on(LocalDate d) {
            this.start = null;
            this.date = d;
            return this;
        }

        TaskBuilder minutes(int m) {
            this.minutes = m;
            return this;
        }

        TaskBuilder done(boolean d) {
            this.done = d;
            return this;
        }

        TaskBuilder due(LocalDate d, LocalDateTime completed) {
            this.due = d;
            this.completedAt = completed;
            return this;
        }

        TaskBuilder carry(int c) {
            this.carry = c;
            return this;
        }

        TaskBuilder actual(LocalDateTime s, int m) {
            this.actualStart = s;
            this.actualMinutes = m;
            return this;
        }

        PatternTaskSample add() {
            PatternTaskSample t = new PatternTaskSample(id, routineId, routineName, null,
                    goalId, goalName, "📘", "#3D5AFE", temporary, goalStart,
                    name, null, done,
                    start, start != null ? start.plusMinutes(minutes) : null, minutes,
                    date, due, completedAt, carry,
                    actualStart, actualStart != null ? actualStart.plusMinutes(actualMinutes) : null, actualMinutes);
            tasks.add(t);
            return t;
        }
    }
}
