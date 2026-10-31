package com.naleul.naleul.domain.planner.service;

import com.naleul.naleul.domain.lifePattern.enums.LifePatternType;
import com.naleul.naleul.domain.lifePattern.service.FixedBlockResolver;
import com.naleul.naleul.domain.lifePattern.support.DayFixedBlocks;
import com.naleul.naleul.domain.lifePattern.support.FixedBlock;
import com.naleul.naleul.domain.planner.dto.request.TaskBatchScheduleRequest;
import com.naleul.naleul.domain.planner.dto.request.TaskScheduleRequest;
import com.naleul.naleul.domain.planner.dto.response.TaskScheduleResponse;
import com.naleul.naleul.domain.pattern.service.TaskScheduleLogWriter;
import com.naleul.naleul.domain.planner.engine.HourlyEfficiency;
import com.naleul.naleul.domain.planner.engine.PlacementCandidate.Window;
import com.naleul.naleul.domain.planner.entity.PlannerSetting;
import com.naleul.naleul.domain.planner.repository.PlacementQueryRepository;
import com.naleul.naleul.domain.planner.support.DateTimeRange;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.enums.PlacedBy;
import com.naleul.naleul.domain.task.enums.TaskPriority;
import com.naleul.naleul.domain.task.service.TaskFinder;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 드래그 규칙 (명세 3-4, v0.2):
 *  - 옮긴 Task 는 잠긴다
 *  - 고정 시간 위에 놓아도 저장되고 덮는다
 *  - 잠긴 Task 와는 한 시각에 2개까지 겹칠 수 있고 3개째는 409, 엔진 블록과 겹치면 밀어내고 다시 배치
 * 기준: 오늘 11/18 09:00, 고정 시간 점심 12~13 · 저녁 18~19
 */
class TaskScheduleServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);
    private static final LocalDate TOMORROW = TODAY.plusDays(1);
    private long seq = 1;
    private final List<Task> db = new ArrayList<>();

    private final PlacementQueryRepository repo = new PlacementQueryRepository() {
        public List<Task> findForPlacement(Long userId, LocalDate date, LocalDateTime rangeStart, LocalDateTime rangeEnd) {
            return db.stream().filter(t -> t.isPlaced()
                    ? t.getPlannedStartAt().isBefore(rangeEnd) && t.getPlannedEndAt().isAfter(rangeStart)
                    : date.equals(t.getRoutineDate())).toList();
        }
        public List<Task> findOneForPlacement(Long userId, Long taskId) {
            return db.stream().filter(t -> taskId.equals(t.getTaskId())).toList();
        }
        public List<Object[]> findEfficiencySamples(Long userId, LocalDateTime from, LocalDateTime to) { return List.of(); }
        public List<Object[]> findRoutineActualMinutes(Collection<Long> routineIds, LocalDateTime from) { return List.of(); }
        public List<Object[]> findGoalSlotsJson(Collection<Long> goalIds) { return List.of(); }
    };

    private final FixedBlockResolver resolver = new FixedBlockResolver(null, null) {
        @Override
        public DayFixedBlocks resolveDay(Long userId, LocalDate date) {
            List<FixedBlock> blocks = List.of(
                    new FixedBlock(3L, LifePatternType.LUNCH, "점심", null, date, date.atTime(12, 0), date.atTime(13, 0), false, false),
                    new FixedBlock(4L, LifePatternType.DINNER, "저녁", null, date, date.atTime(18, 0), date.atTime(19, 0), false, false));
            return new DayFixedBlocks(date, blocks, blocks, new DateTimeRange(date.atTime(7, 0), date.plusDays(1).atStartOfDay()));
        }
    };

    private final PlacementContextLoader loader = new PlacementContextLoader(null, null) {
        public HourlyEfficiency efficiency(Long userId, LocalDate today) { return HourlyEfficiency.defaultCurve(0); }
        public Map<Long, Integer> adjustedRoutineMinutes(Map<Long, Integer> planned, LocalDate today) { return Map.of(); }
        public Map<Long, List<Window>> preferredWindows(Collection<Long> goalIds) { return Map.of(); }
    };

    private final PlannerSettingService settings = new PlannerSettingService(null) {
        @Override
        public PlannerSetting getOrCreateForUpdate(Long userId) { return PlannerSetting.createDefault(userId); }
    };

    private final PlannerClock clock = new PlannerClock() {
        public LocalDate today() { return TODAY; }
        public LocalDateTime now() { return TODAY.atTime(9, 0); }
    };

    private final TaskFinder finder = new TaskFinder(null) {
        @Override
        public Task findTaskWithDetails(Long taskId, Long userId) {
            return db.stream().filter(t -> taskId.equals(t.getTaskId())).findFirst()
                    .orElseThrow(() -> new CustomException(ErrorCode.TASK_NOT_FOUND));
        }
    };

    private final DayPlacementService placement = new DayPlacementService(repo, resolver, loader, settings, clock,
            TaskScheduleLogWriter.noop());
    private final TaskScheduleService service = new TaskScheduleService(finder, repo, placement, resolver, settings,
            null, null, clock, TaskScheduleLogWriter.noop());

    private Task engine(LocalDateTime s, int minutes) {
        Task t = Task.createOneTimeFromAi(null, null, null, null, "엔진" + seq, null, minutes, s.toLocalDate(), null);
        ReflectionTestUtils.setField(t, "taskId", seq++);
        t.placeByEngine(s, s.plusMinutes(minutes), "r", "DEFAULT_CURVE");
        db.add(t);
        return t;
    }

    private Task locked(LocalDateTime s, int minutes) {
        Task t = Task.create(null, null, null, "약속" + seq, TaskPriority.A, s, s.plusMinutes(minutes));
        ReflectionTestUtils.setField(t, "taskId", seq++);
        db.add(t);
        return t;
    }

    private static TaskScheduleRequest req(LocalDateTime s, int minutes) {
        return new TaskScheduleRequest(s, s.plusMinutes(minutes));
    }

    @Test
    @DisplayName("드래그하면 그 시간으로 잠긴다 (엔진 블록 → 사용자 블록)")
    void locksAfterDrag() {
        Task t = engine(TOMORROW.atTime(9, 0), 60);
        TaskScheduleResponse res = service.reschedule(1L, t.getTaskId(), req(TOMORROW.atTime(20, 0), 60));
        assertThat(t.getPlannedStartAt()).isEqualTo(TOMORROW.atTime(20, 0));
        assertThat(t.isScheduleLocked()).isTrue();
        assertThat(t.getPlacedBy()).isEqualTo(PlacedBy.USER);
        assertThat(res.task().locked()).isTrue();
        assertThat(res.moved()).isEmpty();
    }

    @Test
    @DisplayName("저녁 고정 시간 위에 놓아도 저장되고, 덮은 고정 시간을 알려준다")
    void coversFixedTime() {
        Task dinnerAppointment = locked(TOMORROW.atTime(10, 0), 60);
        TaskScheduleResponse res = service.reschedule(1L, dinnerAppointment.getTaskId(), req(TOMORROW.atTime(18, 0), 120));
        assertThat(dinnerAppointment.getPlannedStartAt()).isEqualTo(TOMORROW.atTime(18, 0));
        assertThat(res.coveredFixedBlocks()).extracting(TaskScheduleResponse.CoveredFixed::title).containsExactly("저녁");
    }

    @Test
    @DisplayName("잠긴 Task 1개와는 겹쳐 둘 수 있다 (한 시각에 2개까지)")
    void allowsTwoAtOnce() {
        locked(TOMORROW.atTime(15, 0), 60);
        Task mine = engine(TOMORROW.atTime(9, 0), 60);
        service.reschedule(1L, mine.getTaskId(), req(TOMORROW.atTime(15, 30), 60));
        assertThat(mine.getPlannedStartAt()).isEqualTo(TOMORROW.atTime(15, 30));
        assertThat(mine.isScheduleLocked()).isTrue();
    }

    @Test
    @DisplayName("이미 2개가 겹친 순간과 겹치면 409 TIMEBLOCK_CONFLICT, 아무것도 바뀌지 않는다")
    void conflictWhenThirdAtOnce() {
        locked(TOMORROW.atTime(15, 0), 60);
        locked(TOMORROW.atTime(15, 30), 60);
        Task mine = engine(TOMORROW.atTime(9, 0), 60);
        try {
            service.reschedule(1L, mine.getTaskId(), req(TOMORROW.atTime(15, 40), 10));
            throw new AssertionError("expected conflict");
        } catch (CustomException e) {
            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TIMEBLOCK_CONFLICT);
        }
        assertThat(mine.getPlannedStartAt()).isEqualTo(TOMORROW.atTime(9, 0));
    }

    @Test
    @DisplayName("2개가 겹쳐도 서로 다른 순간이면 3개째를 놓을 수 있다 (15~15:30 · 15:30~16 사이 15:00~16:00)")
    void twoSeparateOverlapsAreFine() {
        locked(TOMORROW.atTime(15, 0), 30);
        locked(TOMORROW.atTime(15, 30), 30);
        Task mine = engine(TOMORROW.atTime(9, 0), 60);
        service.reschedule(1L, mine.getTaskId(), req(TOMORROW.atTime(15, 0), 60));
        assertThat(mine.getPlannedStartAt()).isEqualTo(TOMORROW.atTime(15, 0));
    }

    @Test
    @DisplayName("묶음 이동: 연속된 Task 들을 한 번에 옮기고, 서로의 원래 자리는 겹침으로 보지 않는다")
    void batchMove() {
        Task a = locked(TOMORROW.atTime(9, 0), 30);
        Task b = locked(TOMORROW.atTime(9, 30), 30);
        Task c = locked(TOMORROW.atTime(10, 0), 30);
        // 30분 뒤로 — b·c 의 새 자리는 b·c 의 원래 자리와 겹치지만 같이 옮기므로 괜찮다
        service.rescheduleBatch(1L, List.of(
                new TaskBatchScheduleRequest.Item(a.getTaskId(), TOMORROW.atTime(9, 30), TOMORROW.atTime(10, 0)),
                new TaskBatchScheduleRequest.Item(b.getTaskId(), TOMORROW.atTime(10, 0), TOMORROW.atTime(10, 30)),
                new TaskBatchScheduleRequest.Item(c.getTaskId(), TOMORROW.atTime(10, 30), TOMORROW.atTime(11, 0))));
        assertThat(a.getPlannedStartAt()).isEqualTo(TOMORROW.atTime(9, 30));
        assertThat(b.getPlannedStartAt()).isEqualTo(TOMORROW.atTime(10, 0));
        assertThat(c.getPlannedStartAt()).isEqualTo(TOMORROW.atTime(10, 30));
    }

    @Test
    @DisplayName("묶음 이동: 하나라도 자리가 없으면 409, 아무것도 바뀌지 않는다")
    void batchMoveAllOrNothing() {
        locked(TOMORROW.atTime(14, 0), 60);
        locked(TOMORROW.atTime(14, 0), 60);
        Task a = locked(TOMORROW.atTime(9, 0), 30);
        Task b = locked(TOMORROW.atTime(9, 30), 30);
        try {
            service.rescheduleBatch(1L, List.of(
                    new TaskBatchScheduleRequest.Item(a.getTaskId(), TOMORROW.atTime(13, 0), TOMORROW.atTime(13, 30)),
                    new TaskBatchScheduleRequest.Item(b.getTaskId(), TOMORROW.atTime(14, 0), TOMORROW.atTime(14, 30))));
            throw new AssertionError("expected conflict");
        } catch (CustomException e) {
            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TIMEBLOCK_CONFLICT);
        }
        assertThat(a.getPlannedStartAt()).isEqualTo(TOMORROW.atTime(9, 0));
        assertThat(b.getPlannedStartAt()).isEqualTo(TOMORROW.atTime(9, 30));
    }

    @Test
    @DisplayName("엔진 블록 위에 놓으면 그 블록은 밀려나 그날 다른 빈 시간으로 다시 배치된다")
    void pushesEngineBlock() {
        Task victim = engine(TOMORROW.atTime(20, 0), 60);
        Task mine = locked(TOMORROW.atTime(10, 0), 60);

        TaskScheduleResponse res = service.reschedule(1L, mine.getTaskId(), req(TOMORROW.atTime(20, 0), 60));

        assertThat(res.moved()).extracting(TaskScheduleResponse.Moved::taskId).containsExactly(victim.getTaskId());
        assertThat(res.moved().get(0).fromStart()).isEqualTo(TOMORROW.atTime(20, 0));
        assertThat(victim.isPlaced()).isTrue();
        LocalDateTime vs = victim.getPlannedStartAt();
        LocalDateTime ve = victim.getPlannedEndAt();
        // 옮긴 Task(20~21시) ± 10분, 점심·저녁과 겹치지 않음
        assertThat(vs.isBefore(TOMORROW.atTime(21, 10)) && ve.isAfter(TOMORROW.atTime(19, 50))).isFalse();
        assertThat(vs.isBefore(TOMORROW.atTime(13, 0)) && ve.isAfter(TOMORROW.atTime(12, 0))).isFalse();
        assertThat(vs.isBefore(TOMORROW.atTime(19, 0)) && ve.isAfter(TOMORROW.atTime(18, 0))).isFalse();
        assertThat(Duration.between(vs, ve).toMinutes()).isEqualTo(60L);
    }

    @Test
    @DisplayName("시간 미정으로 되돌리면 시간·잠금이 풀리고 날짜는 남는다 (루틴은 불가)")
    void unschedule() {
        Task t = locked(TOMORROW.atTime(10, 0), 30);
        ReflectionTestUtils.setField(t, "routineDate", TOMORROW);
        service.unschedule(1L, t.getTaskId());
        assertThat(t.isPlaced()).isFalse();
        assertThat(t.isScheduleLocked()).isFalse();
        assertThat(t.getRoutineDate()).isEqualTo(TOMORROW);
    }
}
