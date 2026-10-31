package com.naleul.naleul.domain.planner.service;

import com.naleul.naleul.domain.lifePattern.enums.LifePatternType;
import com.naleul.naleul.domain.lifePattern.service.FixedBlockResolver;
import com.naleul.naleul.domain.lifePattern.support.DayFixedBlocks;
import com.naleul.naleul.domain.lifePattern.support.FixedBlock;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DayPlacementService: Task 분류(자리 차지 / 배치 대상 / 비우기)와 다른 날로 넘기기를 검증.
 * DB 대신 메모리 가짜 객체를 쓴다 (Mockito 없이도 읽기 쉽게).
 *
 * 기준: 오늘 = 11/18(수) 09:00, 기본 패턴(수면 00~07, 점심 12~13, 저녁 18~19)
 */
class DayPlacementServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);
    private static final LocalDate TOMORROW = TODAY.plusDays(1);
    private long seq = 1;

    // ── 가짜 의존성 ───────────────────────────────────────

    private final List<Task> db = new ArrayList<>();
    private final Map<LocalDate, List<DateTimeRange>> extraFixed = new HashMap<>();

    private final PlacementQueryRepository repo = new PlacementQueryRepository() {
        @Override
        public List<Task> findForPlacement(Long userId, LocalDate date, LocalDateTime rangeStart, LocalDateTime rangeEnd) {
            return db.stream().filter(t -> t.isPlaced()
                    ? t.getPlannedStartAt().isBefore(rangeEnd) && t.getPlannedEndAt().isAfter(rangeStart)
                    : date.equals(t.getRoutineDate())).toList();
        }

        @Override
        public List<Task> findOneForPlacement(Long userId, Long taskId) {
            return db.stream().filter(t -> taskId.equals(t.getTaskId())).toList();
        }

        @Override
        public List<Object[]> findEfficiencySamples(Long userId, LocalDateTime from, LocalDateTime to) {
            return List.of();
        }

        @Override
        public List<Object[]> findRoutineActualMinutes(Collection<Long> routineIds, LocalDateTime from) {
            return List.of();
        }

        @Override
        public List<Object[]> findGoalSlotsJson(Collection<Long> goalIds) {
            return List.of();
        }
    };

    private final FixedBlockResolver resolver = new FixedBlockResolver(null, null) {
        @Override
        public DayFixedBlocks resolveDay(Long userId, LocalDate date) {
            List<FixedBlock> blocks = new ArrayList<>(List.of(
                    block(LifePatternType.LUNCH, date.atTime(12, 0), date.atTime(13, 0)),
                    block(LifePatternType.DINNER, date.atTime(18, 0), date.atTime(19, 0))));
            for (DateTimeRange r : extraFixed.getOrDefault(date, List.of())) {
                blocks.add(block(LifePatternType.CUSTOM, r.start(), r.end()));
            }
            return new DayFixedBlocks(date, blocks, blocks,
                    new DateTimeRange(date.atTime(7, 0), date.plusDays(1).atStartOfDay()));
        }
    };

    private final PlacementContextLoader loader = new PlacementContextLoader(null, null) {
        @Override
        public HourlyEfficiency efficiency(Long userId, LocalDate today) {
            return HourlyEfficiency.defaultCurve(0);
        }

        @Override
        public Map<Long, Integer> adjustedRoutineMinutes(Map<Long, Integer> planned, LocalDate today) {
            return Map.of();
        }

        @Override
        public Map<Long, List<Window>> preferredWindows(Collection<Long> goalIds) {
            return Map.of();
        }
    };

    private final PlannerSettingService settings = new PlannerSettingService(null) {
        @Override
        public PlannerSetting getOrCreateForUpdate(Long userId) {
            return PlannerSetting.createDefault(userId);
        }
    };

    private final PlannerClock clock = new PlannerClock() {
        @Override
        public LocalDate today() {
            return TODAY;
        }

        @Override
        public LocalDateTime now() {
            return TODAY.atTime(9, 0);
        }
    };

    private final DayPlacementService service = new DayPlacementService(repo, resolver, loader, settings, clock,
            TaskScheduleLogWriter.noop());

    // ── 헬퍼 ─────────────────────────────────────────────

    private static FixedBlock block(LifePatternType type, LocalDateTime s, LocalDateTime e) {
        return new FixedBlock(1L, type, type.name(), null, s.toLocalDate(), s, e, false, false);
    }

    private Task unplaced(LocalDate date, int minutes, LocalDate due) {
        Task t = Task.createOneTimeFromAi(null, null, null, null, "할 일" + seq, null, minutes, date, due);
        ReflectionTestUtils.setField(t, "taskId", seq++);
        db.add(t);
        return t;
    }

    private Task locked(LocalDateTime s, LocalDateTime e) {
        Task t = Task.create(null, null, null, "약속" + seq, TaskPriority.A, s, e);
        ReflectionTestUtils.setField(t, "taskId", seq++);
        db.add(t);
        return t;
    }

    private Task enginePlaced(LocalDateTime s, LocalDateTime e) {
        Task t = unplaced(s.toLocalDate(), (int) java.time.Duration.between(s, e).toMinutes(), null);
        t.placeByEngine(s, e, "이전 배치", "DEFAULT_CURVE");
        return t;
    }

    private static boolean overlaps(Task a, LocalDateTime s, LocalDateTime e) {
        return a.getPlannedStartAt().isBefore(e) && s.isBefore(a.getPlannedEndAt());
    }

    // ── 테스트 ───────────────────────────────────────────

    @Test
    @DisplayName("FILL: 시간 없는 Task는 배치되고, 잠긴 Task·기존 엔진 블록은 그대로다")
    void fill() {
        Task todo = unplaced(TOMORROW, 60, null);
        Task appointment = locked(TOMORROW.atTime(10, 0), TOMORROW.atTime(11, 0));
        Task kept = enginePlaced(TOMORROW.atTime(15, 0), TOMORROW.atTime(16, 0));

        PlacementOutcome out = service.placeDay(1L, TOMORROW, PlacementMode.FILL, null, false);

        assertThat(out.placed()).hasSize(1);
        assertThat(todo.isPlaced()).isTrue();
        assertThat(todo.getPlacedBy()).isEqualTo(PlacedBy.ENGINE);
        assertThat(todo.getPlacementReason()).endsWith("배치했어요.");
        assertThat(overlaps(todo, TOMORROW.atTime(9, 50), TOMORROW.atTime(11, 10))).isFalse(); // 약속 ±10분 피함
        assertThat(overlaps(todo, TOMORROW.atTime(12, 0), TOMORROW.atTime(13, 0))).isFalse();  // 점심 피함
        assertThat(appointment.getPlannedStartAt()).isEqualTo(TOMORROW.atTime(10, 0));
        assertThat(kept.getPlannedStartAt()).isEqualTo(TOMORROW.atTime(15, 0));
    }

    @Test
    @DisplayName("REPLAN: 엔진 블록은 다시 배치되고 잠긴 Task는 절대 움직이지 않는다")
    void replan() {
        Task engine = enginePlaced(TOMORROW.atTime(22, 0), TOMORROW.atTime(23, 0));
        Task appointment = locked(TOMORROW.atTime(9, 0), TOMORROW.atTime(10, 0));

        PlacementOutcome out = service.placeDay(1L, TOMORROW, PlacementMode.REPLAN, null, false);

        assertThat(out.placed()).extracting(PlacementOutcome.PlacedBlock::taskId).containsExactly(engine.getTaskId());
        assertThat(engine.getPlacementReason()).isNotNull();
        assertThat(appointment.getPlannedStartAt()).isEqualTo(TOMORROW.atTime(9, 0));
        assertThat(appointment.isScheduleLocked()).isTrue();
    }

    @Test
    @DisplayName("REPAIR: 새 고정 시간과 겹치는 엔진 블록만 옮기고, 안 겹치는 블록과 미배치 Task는 그대로")
    void repair() {
        Task clash = enginePlaced(TOMORROW.atTime(9, 0), TOMORROW.atTime(10, 0));
        Task fine = enginePlaced(TOMORROW.atTime(20, 0), TOMORROW.atTime(21, 0));
        Task waiting = unplaced(TOMORROW, 30, null);
        extraFixed.put(TOMORROW, List.of(new DateTimeRange(TOMORROW.atTime(8, 30), TOMORROW.atTime(11, 0)))); // 새로 추가한 고정 시간

        service.repairDay(1L, TOMORROW);

        assertThat(overlaps(clash, TOMORROW.atTime(8, 30), TOMORROW.atTime(11, 0))).isFalse();
        assertThat(fine.getPlannedStartAt()).isEqualTo(TOMORROW.atTime(20, 0));
        assertThat(waiting.isPlaced()).isFalse(); // 미배치는 22시 작업 몫
    }

    @Test
    @DisplayName("오늘이면 지금(09:00) 이전 엔진 블록은 지난 기록이라 REPLAN에서도 유지")
    void keepsStartedBlocksToday() {
        Task past = enginePlaced(TODAY.atTime(7, 30), TODAY.atTime(8, 30));
        service.placeDay(1L, TODAY, PlacementMode.REPLAN, null, false);
        assertThat(past.getPlannedStartAt()).isEqualTo(TODAY.atTime(7, 30));
    }

    @Test
    @DisplayName("지난 날짜는 배치하지 않는다")
    void pastDate() {
        Task old = unplaced(TODAY.minusDays(1), 30, null);
        PlacementOutcome out = service.placeDay(1L, TODAY.minusDays(1), PlacementMode.FILL, null, true);
        assertThat(out.placed()).isEmpty();
        assertThat(old.isPlaced()).isFalse();
    }

    @Test
    @DisplayName("빈 시간이 없으면 마감일 안의 다음 날로 넘기고, 이유에 '옮겼어요'가 붙는다")
    void overflowToNextDay() {
        // 오늘 09:00 이후를 전부 고정 시간으로 막음
        extraFixed.put(TODAY, List.of(new DateTimeRange(TODAY.atTime(9, 0), TODAY.plusDays(1).atStartOfDay())));
        Task call = unplaced(TODAY, 30, TODAY.plusDays(2));

        PlacementOutcome out = service.placeDay(1L, TODAY, PlacementMode.FILL, null, true);

        assertThat(out.placed()).isEmpty();
        assertThat(out.movedToOtherDays()).hasSize(1);
        assertThat(call.getRoutineDate()).isEqualTo(TOMORROW);
        assertThat(call.getPlannedStartAt().toLocalDate()).isEqualTo(TOMORROW);
        assertThat(call.getPlacementReason()).startsWith("오늘은 빈 시간이 부족해");
        assertThat(call.getPlacementReasonCodes()).contains("MOVED_TO_OTHER_DAY");
    }

    @Test
    @DisplayName("넘길 수도 없으면 날짜만 남는다 (시간 미정)")
    void unscheduled() {
        extraFixed.put(TOMORROW, List.of(new DateTimeRange(TOMORROW.atTime(7, 0), TOMORROW.plusDays(1).atStartOfDay())));
        extraFixed.put(TOMORROW.plusDays(1), List.of(new DateTimeRange(TOMORROW.plusDays(1).atTime(7, 0), TOMORROW.plusDays(2).atStartOfDay())));
        Task t = unplaced(TOMORROW, 30, null);

        PlacementOutcome out = service.placeDay(1L, TOMORROW, PlacementMode.FILL, null, true);

        assertThat(out.unscheduled()).containsExactly(t.getTaskId());
        assertThat(t.isPlaced()).isFalse();
        assertThat(t.getRoutineDate()).isEqualTo(TOMORROW);
    }

    @Test
    @DisplayName("완료한 Task는 옮기지 않고 자리만 차지한다")
    void completedStays() {
        Task done = enginePlaced(TOMORROW.atTime(10, 0), TOMORROW.atTime(11, 0));
        done.complete();
        service.placeDay(1L, TOMORROW, PlacementMode.REPLAN, null, false);
        assertThat(done.getPlannedStartAt()).isEqualTo(TOMORROW.atTime(10, 0));
    }
}
