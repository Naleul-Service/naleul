package com.naleul.naleul.domain.lifePattern.service;

import com.naleul.naleul.domain.lifePattern.dto.request.LifePatternScopedChangeRequest;
import com.naleul.naleul.domain.lifePattern.dto.response.LifePatternScopedChangeResponse;
import com.naleul.naleul.domain.lifePattern.entity.LifePattern;
import com.naleul.naleul.domain.lifePattern.entity.LifePatternOverride;
import com.naleul.naleul.domain.lifePattern.enums.LifePatternType;
import com.naleul.naleul.domain.lifePattern.repository.LifePatternOverrideRepository;
import com.naleul.naleul.domain.lifePattern.repository.LifePatternRepository;
import com.naleul.naleul.domain.planner.enums.ChangeScope;
import com.naleul.naleul.domain.planner.service.PlannerSettingService;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.service.UserFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/** 고정 시간 범위 변경 (이번 주 / 앞으로) */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LifePatternScopedChangeTest {

    private static final Long USER = 1L;
    private static final Long PATTERN_ID = 5L;
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 6);   // 화
    private static final LocalDate WED = LocalDate.of(2026, 10, 7);

    @Mock LifePatternRepository lifePatternRepository;
    @Mock LifePatternOverrideRepository overrideRepository;
    @Mock PlannerSettingService plannerSettingService;
    @Mock FixedBlockResolver fixedBlockResolver;
    @Mock UserFinder userFinder;
    @Mock PlannerClock clock;
    @Mock ApplicationEventPublisher eventPublisher;

    LifePatternService service;

    @BeforeEach
    void setUp() {
        service = new LifePatternService(lifePatternRepository, overrideRepository, plannerSettingService,
                fixedBlockResolver, userFinder, clock, eventPublisher);
        given(clock.today()).willReturn(TODAY);
        given(overrideRepository.findByPatternAndDate(anyLong(), any())).willReturn(Optional.empty());
        given(overrideRepository.findAllInRange(anyLong(), any(), any())).willReturn(List.of());
    }

    private LifePattern lunch(List<DayOfWeek> days) {
        LifePattern p = LifePattern.create(mock(User.class), LifePatternType.LUNCH, "점심", "🍚",
                LocalTime.of(12, 0), LocalTime.of(13, 0), days, 1);
        ReflectionTestUtils.setField(p, "lifePatternId", PATTERN_ID);
        given(lifePatternRepository.findOwned(PATTERN_ID, USER)).willReturn(Optional.of(p));
        given(lifePatternRepository.findAllByUserId(USER)).willReturn(List.of(p));
        return p;
    }

    private LifePatternScopedChangeRequest req(ChangeScope scope) {
        return new LifePatternScopedChangeRequest(WED, LocalTime.of(12, 30), LocalTime.of(13, 30), scope);
    }

    @Test
    @DisplayName("이번 주: 수요일~일요일 매일 같은 시간으로, 이번 주에 비운 날은 그대로")
    void week_everyDay_keepsSkipped() {
        LifePattern p = lunch(null);   // 매일
        LifePatternOverride skippedFri = LifePatternOverride.skippedOn(p, USER, WED.plusDays(2));
        given(overrideRepository.findByPatternAndDate(PATTERN_ID, WED.plusDays(2))).willReturn(Optional.of(skippedFri));

        LifePatternScopedChangeResponse res = service.changeWithScope(USER, PATTERN_ID, req(ChangeScope.WEEK));

        assertThat(res.changedDates()).containsExactly(WED, WED.plusDays(1), WED.plusDays(3), WED.plusDays(4));
        assertThat(skippedFri.isSkipped()).isTrue();
        verify(overrideRepository, times(4)).save(any(LifePatternOverride.class));
        // 기본값은 그대로
        assertThat(p.getStartTime()).isEqualTo(LocalTime.of(12, 0));
    }

    @Test
    @DisplayName("이번 주: 평일 패턴이면 수·목·금만")
    void week_weekdaysOnly() {
        lunch(List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));

        LifePatternScopedChangeResponse res = service.changeWithScope(USER, PATTERN_ID, req(ChangeScope.WEEK));

        assertThat(res.changedDates()).containsExactly(WED, WED.plusDays(1), WED.plusDays(2));
    }

    @Test
    @DisplayName("앞으로: 지난 28일은 예전 시간으로 고정하고, 기본값을 바꾸고, 이후 '시간만 바꾼' 예외를 지운다")
    void following_freezesPast_updatesDefault() {
        LifePattern p = lunch(null);
        // 지난 날 중 하루는 이미 예외가 있음 → 다시 만들지 않음
        LifePatternOverride existing = LifePatternOverride.moved(p, USER, WED.minusDays(3), LocalTime.of(11, 0), LocalTime.of(12, 0));
        given(overrideRepository.findAllInRange(eq(USER), any(), any())).willReturn(List.of(existing));

        service.changeWithScope(USER, PATTERN_ID, req(ChangeScope.FOLLOWING));

        ArgumentCaptor<LifePatternOverride> saved = ArgumentCaptor.forClass(LifePatternOverride.class);
        verify(overrideRepository, times(27)).save(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(o -> {
            assertThat(o.getTargetDate()).isBefore(WED);
            assertThat(o.getStartTime()).isEqualTo(LocalTime.of(12, 0));   // 예전 시간
        });
        assertThat(saved.getAllValues()).noneMatch(o -> o.getTargetDate().equals(WED.minusDays(3)));
        assertThat(p.getStartTime()).isEqualTo(LocalTime.of(12, 30));       // 새 기본값
        assertThat(p.getEndTime()).isEqualTo(LocalTime.of(13, 30));
        verify(overrideRepository).deleteMovedFrom(p, WED);
    }

    @Test
    @DisplayName("앞으로 이 요일마다: 수요일만 요일별 시간으로, 지난 수요일들만 예전 시간으로 고정, 기본값은 그대로")
    void weekday_setsDayTimeOnly() {
        LifePattern p = lunch(null);

        service.changeWithScope(USER, PATTERN_ID, req(ChangeScope.WEEKDAY));

        assertThat(p.getStartTime()).isEqualTo(LocalTime.of(12, 0));   // 기본값 그대로
        assertThat(p.dayTimeOn(DayOfWeek.WEDNESDAY).getStartTime()).isEqualTo(LocalTime.of(12, 30));
        assertThat(p.dayTimeOn(DayOfWeek.THURSDAY)).isNull();
        ArgumentCaptor<LifePatternOverride> saved = ArgumentCaptor.forClass(LifePatternOverride.class);
        verify(overrideRepository, times(4)).save(saved.capture());   // 지난 28일 중 수요일 4번
        assertThat(saved.getAllValues()).allSatisfy(o -> {
            assertThat(o.getTargetDate().getDayOfWeek()).isEqualTo(DayOfWeek.WEDNESDAY);
            assertThat(o.getStartTime()).isEqualTo(LocalTime.of(12, 0));
        });
    }

    @Test
    @DisplayName("앞으로 매일: 요일별 시간도 모두 지우고 새 기본값으로")
    void following_clearsDayTimes() {
        LifePattern p = lunch(null);
        p.setDayTime(DayOfWeek.SATURDAY, LocalTime.of(13, 0), LocalTime.of(14, 0), 0);

        service.changeWithScope(USER, PATTERN_ID, req(ChangeScope.FOLLOWING));

        assertThat(p.getDayTimes()).isEmpty();
        assertThat(p.getStartTime()).isEqualTo(LocalTime.of(12, 30));
    }
}
