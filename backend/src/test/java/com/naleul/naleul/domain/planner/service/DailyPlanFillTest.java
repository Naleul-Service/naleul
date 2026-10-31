package com.naleul.naleul.domain.planner.service;

import com.naleul.naleul.domain.planner.dto.response.FillResponse;
import com.naleul.naleul.domain.planner.repository.DailyPlanRepository;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/** "AI로 TimeTable 배치하기" — 시간 미정 Task 만 빈 시간에 채우기 (FILL) */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DailyPlanFillTest {

    private static final Long USER = 1L;
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 6);

    @Mock DailyPlanRepository dailyPlanRepository;
    @Mock DayPlacementService dayPlacementService;
    @Mock PlannerClock clock;
    DailyPlanService service;

    @BeforeEach
    void setUp() {
        service = new DailyPlanService(dailyPlanRepository, dayPlacementService, clock);
        given(clock.today()).willReturn(TODAY);
        given(dayPlacementService.placeDay(eq(USER), any(), eq(PlacementMode.FILL), isNull(), eq(false)))
                .willAnswer(inv -> {
                    LocalDate d = inv.getArgument(1);
                    PlacementOutcome.PlacedBlock b = new PlacementOutcome.PlacedBlock(1L, d, d.atTime(9, 0), d.atTime(10, 0), null);
                    return new PlacementOutcome(d, List.of(b), List.of(), d.equals(TODAY) ? List.of(9L) : List.of());
                });
    }

    @Test
    @DisplayName("기간의 날짜마다 FILL 로 채우고 합계를 돌려준다 (지난 날짜는 건너뜀)")
    void fillsEachDayFromToday() {
        FillResponse r = service.fill(USER, TODAY.minusDays(2), TODAY.plusDays(2));

        assertThat(r.days()).extracting(FillResponse.Day::date)
                .containsExactly(TODAY, TODAY.plusDays(1), TODAY.plusDays(2));
        assertThat(r.placedCount()).isEqualTo(3);
        assertThat(r.unscheduledCount()).isEqualTo(1);
        verify(dayPlacementService, never()).placeDay(eq(USER), eq(TODAY.minusDays(1)), any(), any(), anyBoolean());
        // 확인 배너(daily_plan)는 만들지 않는다
        verifyNoInteractions(dailyPlanRepository);
    }

    @Test
    @DisplayName("7일을 넘기면 400")
    void tooLong() {
        assertThatThrownBy(() -> service.fill(USER, TODAY, TODAY.plusDays(7)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TIMETABLE_INVALID_RANGE);
    }
}
