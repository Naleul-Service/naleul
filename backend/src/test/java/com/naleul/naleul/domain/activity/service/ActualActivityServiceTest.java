package com.naleul.naleul.domain.activity.service;

import com.naleul.naleul.domain.activity.dto.request.ActualActivityRequest;
import com.naleul.naleul.domain.activity.entity.ActualActivity;
import com.naleul.naleul.domain.activity.repository.ActualActivityRepository;
import com.naleul.naleul.domain.category.service.CategoryFinder;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.service.TaskFinder;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.service.UserFinder;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ActualActivityServiceTest {

    /** 지금 = 10/6 18:00 */
    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 6, 18, 0);

    @Mock ActualActivityRepository repository;
    @Mock UserFinder userFinder;
    @Mock CategoryFinder categoryFinder;
    @Mock TaskFinder taskFinder;
    @Mock PlannerClock clock;
    @InjectMocks ActualActivityService service;

    User user = mock(User.class);

    @BeforeEach
    void setUp() {
        given(clock.now()).willReturn(NOW);
        given(userFinder.findUser(1L)).willReturn(user);
        given(repository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(repository.findOverlapping(any(), any(), any())).willReturn(List.of());
    }

    static ActualActivityRequest req(LocalDateTime start, LocalDateTime end, Long replacedTaskId) {
        return new ActualActivityRequest("친구 만남", "🍻", start, end, null, replacedTaskId, null);
    }

    @Test
    void 계획한_Task_대신_한_일을_기록하면_Task_이름을_남긴다() {
        Task planned = mock(Task.class);
        given(planned.getTaskId()).willReturn(91L);
        given(planned.getTaskName()).willReturn("영어 공부");
        given(taskFinder.findTaskWithDetails(91L, 1L)).willReturn(planned);

        var res = service.create(1L, req(NOW.minusHours(3), NOW.minusHours(1).minusMinutes(30), 91L));

        assertThat(res.title()).isEqualTo("친구 만남");
        assertThat(res.durationMinutes()).isEqualTo(90);
        assertThat(res.replacedTaskId()).isEqualTo(91L);
        assertThat(res.replacedTaskName()).isEqualTo("영어 공부");
    }

    @Test
    void 아직_오지_않은_시간은_기록할_수_없다() {
        assertThatThrownBy(() -> service.create(1L, req(NOW.minusMinutes(30), NOW.plusMinutes(30), null)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ACTIVITY_FUTURE);
    }

    @Test
    void 끝이_시작보다_빠르거나_24시간을_넘으면_안_된다() {
        assertThatThrownBy(() -> service.create(1L, req(NOW.minusHours(1), NOW.minusHours(2), null)))
                .extracting("errorCode").isEqualTo(ErrorCode.ACTIVITY_INVALID_TIME);
        assertThatThrownBy(() -> service.create(1L, req(NOW.minusHours(26), NOW.minusHours(1), null)))
                .extracting("errorCode").isEqualTo(ErrorCode.ACTIVITY_TOO_LONG);
    }

    @Test
    void 다른_기록과_겹치면_409() {
        ActualActivity other = mock(ActualActivity.class);
        given(other.getActualActivityId()).willReturn(5L);
        given(repository.findOverlapping(any(), any(), any())).willReturn(List.of(other));

        assertThatThrownBy(() -> service.create(1L, req(NOW.minusHours(2), NOW.minusHours(1), null)))
                .extracting("errorCode").isEqualTo(ErrorCode.ACTIVITY_OVERLAP);
    }

    @Test
    void 수정할_때_자기_자신과는_겹쳐도_된다() {
        ActualActivity self = ActualActivity.create(user, "독서", null, NOW.minusHours(2), NOW.minusHours(1),
                null, 91L, "영어 공부", null);
        org.springframework.test.util.ReflectionTestUtils.setField(self, "actualActivityId", 5L);
        given(repository.findOwned(5L, 1L)).willReturn(Optional.of(self));
        given(repository.findOverlapping(any(), any(), any())).willReturn(List.of(self));

        var res = service.update(1L, 5L, req(NOW.minusHours(2), NOW.minusMinutes(30), 91L));

        assertThat(res.durationMinutes()).isEqualTo(90);
        // 대신한 Task 를 그대로 두면 다시 조회하지 않고 이름 스냅샷 유지 (그 Task 가 지워졌어도)
        assertThat(res.replacedTaskName()).isEqualTo("영어 공부");
    }

    @Test
    void 조회_기간은_42일까지() {
        assertThatThrownBy(() -> service.getRange(1L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1)))
                .extracting("errorCode").isEqualTo(ErrorCode.ACTIVITY_INVALID_RANGE);
    }
}
