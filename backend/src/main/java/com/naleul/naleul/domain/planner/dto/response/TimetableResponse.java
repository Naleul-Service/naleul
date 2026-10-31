package com.naleul.naleul.domain.planner.dto.response;

import com.naleul.naleul.domain.lifePattern.dto.response.DayFixedBlocksResponse.DayRange;
import com.naleul.naleul.domain.lifePattern.enums.LifePatternType;
import com.naleul.naleul.domain.planner.enums.DailyPlanSource;
import com.naleul.naleul.domain.planner.enums.DailyPlanStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** GET /api/v1/timetable — 일간(1일)·주간(최대 7일) 공통 (명세 5-2) */
public record TimetableResponse(List<Day> days) {

    public record Day(
            LocalDate date,
            DayRange dayRange,
            List<FixedBlock> fixedBlocks,
            List<TimeBlockTaskResponse> tasks,
            List<TimeBlockTaskResponse> unscheduledTasks,
            Plan plan,
            Stats stats,
            Sleep sleep,
            /** 시간대를 정한 기본 생활 습관 (V56) — 캘린더에 체크할 수 있는 블록으로 그린다 */
            List<HabitBlock> habits,
            /**
             * 이날 못 해서 다른 날로 넘어간 Task 의 흔적 (2026-10). 원래 계획 자리에 점선 · "*" 표시로 그린다.
             * 누르면 옮겨간 날(toDate)로 이동. 넘어간 뒤 다시 이날로 돌아온 Task 는 넣지 않는다.
             */
            List<CarriedOutBlock> carriedOut
    ) {
        public Day(LocalDate date, DayRange dayRange, List<FixedBlock> fixedBlocks, List<TimeBlockTaskResponse> tasks,
                   List<TimeBlockTaskResponse> unscheduledTasks, Plan plan, Stats stats, Sleep sleep) {
            this(date, dayRange, fixedBlocks, tasks, unscheduledTasks, plan, stats, sleep, List.of(), List.of());
        }

        public Day(LocalDate date, DayRange dayRange, List<FixedBlock> fixedBlocks, List<TimeBlockTaskResponse> tasks,
                   List<TimeBlockTaskResponse> unscheduledTasks, Plan plan, Stats stats, Sleep sleep,
                   List<HabitBlock> habits) {
            this(date, dayRange, fixedBlocks, tasks, unscheduledTasks, plan, stats, sleep, habits, List.of());
        }
    }

    /**
     * 이월 흔적. start/end 는 원래 계획 시각 (시간 미정이었으면 둘 다 null → 그날 "시간 미정" 목록에 흐리게)
     * @param toDate 지금 이 Task 가 있는 날 (이월된 날)
     */
    public record CarriedOutBlock(Long taskId, String taskName, String emoji, String goalColorCode,
                                  LocalDateTime start, LocalDateTime end, LocalDate toDate) {
    }

    public record HabitBlock(Long habitId, String name, String emoji, LocalDateTime start, LocalDateTime end,
                             boolean checked) {
    }

    /**
     * 하루 범위를 정한 수면 블록 (자르지 않은 원본 구간). 화면은 기상 전·취침 후 2시간을 함께 보여주고
     * 기상(wake 의 끝)·취침(bed 의 시작)을 끌어서 바꿀 수 있게 한다. 수면 패턴이 없으면 둘 다 null.
     */
    public record Sleep(FixedBlock wake, FixedBlock bed) {
    }

    /**
     * 고정 블록. coveredBy = 사용자가 시간을 정해 이 블록을 덮고 있는 Task 구간 (명세 v0.2)
     * 화면은 coveredBy 구간만큼 빗금을 가리고 Task 를 그린다.
     */
    public record FixedBlock(
            Long lifePatternId,
            LifePatternType patternType,
            String title,
            String emoji,
            LocalDate targetDate,
            LocalDateTime start,
            LocalDateTime end,
            boolean overridden,
            boolean clippedFromPreviousDay,
            List<Covered> coveredBy
    ) {
    }

    public record Covered(Long taskId, LocalDateTime start, LocalDateTime end) {
    }

    /** 그날 TimeTable 생성 기록 (없으면 null) */
    public record Plan(DailyPlanStatus status, DailyPlanSource source, String summary,
                       boolean needsReview, LocalDateTime generatedAt) {
    }

    /**
     * rate = 완료 / 전체 × 100 (Task 가 없으면 null)
     * carriedOver = 이날 못 해서 다음 날로 넘어간 Task 수. total·missed 에 이미 포함되어 있다
     *               (넘어가도 "이날 계획했는데 못 한 일"이므로 실천률이 바뀌지 않게)
     */
    public record Stats(int total, int completed, int missed, Integer rate, int carriedOver) {
        public Stats(int total, int completed, int missed, Integer rate) {
            this(total, completed, missed, rate, 0);
        }
    }
}
