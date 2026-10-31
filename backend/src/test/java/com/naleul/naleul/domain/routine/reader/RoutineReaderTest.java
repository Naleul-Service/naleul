package com.naleul.naleul.domain.routine.reader;

import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.routine.dto.response.RoutineHeatmapResponse;
import com.naleul.naleul.domain.routine.dto.response.RoutineResponse;
import com.naleul.naleul.domain.routine.entity.Routine;
import com.naleul.naleul.domain.routine.repository.RoutineRepository;
import com.naleul.naleul.domain.routine.service.RoutineFinder;
import com.naleul.naleul.domain.task.repository.TaskRepository;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.service.UserFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoutineReaderTest {

    @Mock
    private RoutineRepository routineRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private UserFinder userFinder;

    @Mock
    private RoutineFinder routineFinder;

    private RoutineReader routineReader;

    @BeforeEach
    void setUp() {
        routineReader = new RoutineReader(routineRepository, taskRepository, userFinder, routineFinder, null);
    }

    // ── 공통 픽스처 헬퍼 ──────────────────────────────────

    /**
     * RoutineResponse.from()이 goalCategory/generalCategory를 null 체크 없이 바로 쓰기 때문에,
     * mock(Routine.class)가 아니라 실제 Routine 객체를 spy로 감싸서 routineId만 제어한다.
     */
    private Routine routineWithCategories(Long routineId, GoalCategory goalCategory, GeneralCategory generalCategory) {
        Routine routine = spy(Routine.create(
                mock(User.class), goalCategory, generalCategory,
                "테스트 루틴",
                LocalTime.of(7, 0), LocalTime.of(8, 0),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                List.of(DayOfWeek.MONDAY),
                true
        ));
        given(routine.getRoutineId()).willReturn(routineId);
        return routine;
    }

    private GoalCategory goalCategoryFixture(Long id, String name) {
        GoalCategory goalCategory = mock(GoalCategory.class);
        given(goalCategory.getGoalCategoryId()).willReturn(id);
        given(goalCategory.getGoalCategoryName()).willReturn(name);
        // getUserColor()는 스텁 안 함 → 기본 null 반환, RoutineResponse.from()의 null 체크로 안전하게 처리됨
        return goalCategory;
    }

    private GeneralCategory generalCategoryFixture(Long id, String name) {
        GeneralCategory generalCategory = mock(GeneralCategory.class);
        given(generalCategory.getGeneralCategoryId()).willReturn(id);
        given(generalCategory.getGeneralCategoryName()).willReturn(name);
        return generalCategory;
    }

    /**
     * getHeatmap()은 RoutineResponse.from()을 타지 않고 RoutineHeatmapResponse.of()만 쓰므로
     * goalCategory/generalCategory 값을 아예 스텁하지 않아도 된다 (unnecessary stubbing 방지).
     */
    private Routine routineForHeatmap(Long routineId) {
        Routine routine = spy(Routine.create(
                mock(User.class), mock(GoalCategory.class), mock(GeneralCategory.class),
                "테스트 루틴",
                LocalTime.of(7, 0), LocalTime.of(8, 0),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                List.of(DayOfWeek.MONDAY),
                true
        ));
        given(routine.getRoutineId()).willReturn(routineId);
        return routine;
    }

    // ── getRoutine() ──────────────────────────────────────

    @Nested
    @DisplayName("getRoutine()")
    class GetRoutine {

        @Test
        @DisplayName("소유권 확인된 루틴을 조회해서 응답으로 변환한다")
        void getRoutine_returnsResponse() {
            // given
            User user = mock(User.class);
            GoalCategory goalCategory = goalCategoryFixture(100L, "건강");
            GeneralCategory generalCategory = generalCategoryFixture(200L, "아침 운동");
            Routine routine = routineWithCategories(10L, goalCategory, generalCategory);

            given(userFinder.findUser(1L)).willReturn(user);
            given(routineFinder.findByIdAndUser(10L, user)).willReturn(routine);

            // when
            RoutineResponse response = routineReader.getRoutine(1L, 10L);

            // then
            assertThat(response.getRoutineId()).isEqualTo(10L);
            assertThat(response.getRoutineName()).isEqualTo("테스트 루틴");
            assertThat(response.getGoalCategoryId()).isEqualTo(100L);
            assertThat(response.getGeneralCategoryId()).isEqualTo(200L);
            verify(routineFinder).findByIdAndUser(10L, user);
        }

        @Test
        @DisplayName("소유권 확인은 routineFinder에게 위임하고, 직접 repository를 호출하지 않는다")
        void getRoutine_delegatesOwnershipCheckToFinder() {
            // given
            User user = mock(User.class);
            GoalCategory goalCategory = goalCategoryFixture(100L, "건강");
            GeneralCategory generalCategory = generalCategoryFixture(200L, "아침 운동");
            Routine routine = routineWithCategories(10L, goalCategory, generalCategory);

            given(userFinder.findUser(1L)).willReturn(user);
            given(routineFinder.findByIdAndUser(10L, user)).willReturn(routine);

            // when
            routineReader.getRoutine(1L, 10L);

            // then
            verifyNoInteractions(routineRepository);
        }
    }

    // ── getRoutines() ─────────────────────────────────────

    @Nested
    @DisplayName("getRoutines()")
    class GetRoutines {

        @Test
        @DisplayName("유저의 전체 루틴 목록을 응답으로 변환한다")
        void getRoutines_returnsResponseList() {
            // given
            User user = mock(User.class);
            GoalCategory goalCategory = goalCategoryFixture(100L, "건강");
            GeneralCategory generalCategory = generalCategoryFixture(200L, "아침 운동");
            Routine routine1 = routineWithCategories(1L, goalCategory, generalCategory);
            Routine routine2 = routineWithCategories(2L, goalCategory, generalCategory);

            given(userFinder.findUser(1L)).willReturn(user);
            given(routineRepository.findAllByUser(user)).willReturn(List.of(routine1, routine2));

            // when
            List<RoutineResponse> responses = routineReader.getRoutines(1L);

            // then
            assertThat(responses).hasSize(2);
        }

        @Test
        @DisplayName("루틴이 없으면 빈 리스트를 반환한다")
        void getRoutines_returnsEmptyList_whenNoRoutines() {
            // given
            User user = mock(User.class);
            given(userFinder.findUser(1L)).willReturn(user);
            given(routineRepository.findAllByUser(user)).willReturn(List.of());

            // when
            List<RoutineResponse> responses = routineReader.getRoutines(1L);

            // then
            assertThat(responses).isEmpty();
        }
    }

    // ── getAllRoutinesForDisplay() ────────────────────────

    @Nested
    @DisplayName("getAllRoutinesForDisplay()")
    class GetAllRoutinesForDisplay {

        @Test
        @DisplayName("카테고리/색상 상세 정보를 포함한 루틴 목록을 응답으로 변환한다")
        void getAllRoutinesForDisplay_returnsResponseList() {
            // given
            User user = mock(User.class);
            GoalCategory goalCategory = goalCategoryFixture(100L, "건강");
            GeneralCategory generalCategory = generalCategoryFixture(200L, "아침 운동");
            Routine routine = routineWithCategories(1L, goalCategory, generalCategory);

            given(userFinder.findUser(1L)).willReturn(user);
            given(routineRepository.findAllByUserWithDetails(user)).willReturn(List.of(routine));

            // when
            List<RoutineResponse> responses = routineReader.getAllRoutinesForDisplay(1L);

            // then
            assertThat(responses).hasSize(1);
            verify(routineRepository).findAllByUserWithDetails(user);
        }
    }

    // ── getHeatmap() ──────────────────────────────────────

    @Nested
    @DisplayName("getHeatmap()")
    class GetHeatmap {

        @Test
        @DisplayName("goalCategoryId에 속한 루틴이 없으면 빈 리스트를 반환하고 Task 조회를 하지 않는다")
        void getHeatmap_returnsEmptyList_whenNoRoutines() {
            // given
            given(routineRepository.findAllByGoalCategoryId(1L)).willReturn(List.of());

            // when
            List<RoutineHeatmapResponse> result = routineReader.getHeatmap(1L);

            // then
            assertThat(result).isEmpty();
            verifyNoInteractions(taskRepository);
        }

        @Test
        @DisplayName("루틴별 완료 날짜를 매핑해서 히트맵 응답을 생성한다")
        void getHeatmap_mapsCompletedDatesByRoutine() {
            // given
            Routine routine1 = routineForHeatmap(1L);
            Routine routine2 = routineForHeatmap(2L);
            given(routineRepository.findAllByGoalCategoryId(100L)).willReturn(List.of(routine1, routine2));

            Map<Long, List<LocalDate>> completedDates = Map.of(
                    1L, List.of(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 8)),
                    2L, List.of(LocalDate.of(2026, 9, 8))
            );
            given(taskRepository.findCompletedDatesGroupedByRoutineId(List.of(1L, 2L)))
                    .willReturn(completedDates);

            // when
            List<RoutineHeatmapResponse> result = routineReader.getHeatmap(100L);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).routineId()).isEqualTo(1L);
            assertThat(result.get(0).completedDates()).containsExactly(
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 8)
            );
            verify(taskRepository).findCompletedDatesGroupedByRoutineId(List.of(1L, 2L));
        }

        @Test
        @DisplayName("완료 날짜 매핑에 없는 루틴은 빈 리스트로 처리된다")
        void getHeatmap_usesEmptyListForRoutinesWithoutCompletedDates() {
            // given: routine2는 completedDates 맵에 아예 없는 상황
            Routine routine1 = routineForHeatmap(1L);
            Routine routine2 = routineForHeatmap(2L);
            given(routineRepository.findAllByGoalCategoryId(100L)).willReturn(List.of(routine1, routine2));
            given(taskRepository.findCompletedDatesGroupedByRoutineId(List.of(1L, 2L)))
                    .willReturn(Map.of(1L, List.of(LocalDate.of(2026, 9, 1))));

            // when
            List<RoutineHeatmapResponse> result = routineReader.getHeatmap(100L);

            // then: 예외 없이 정상 처리되고, routine2는 completedDates가 빈 리스트
            assertThat(result).hasSize(2);
            assertThat(result.get(1).completedDates()).isEmpty();
        }
    }
}