package com.naleul.naleul.domain.routine.service;

import com.naleul.naleul.domain.category.service.CategoryFinder;
import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.routine.deleter.RoutineCascadeDeleter;
import com.naleul.naleul.domain.routine.dto.request.RoutineCompleteRequest;
import com.naleul.naleul.domain.routine.dto.request.RoutineCreateRequest;
import com.naleul.naleul.domain.routine.dto.request.RoutineUpdateRequest;
import com.naleul.naleul.domain.routine.dto.response.RoutineResponse;
import com.naleul.naleul.domain.routine.entity.Routine;
import com.naleul.naleul.domain.routine.factory.RoutineFactory;
import com.naleul.naleul.domain.routine.factory.RoutineTaskFactory;
import com.naleul.naleul.domain.routine.repository.RoutineRepository;
import com.naleul.naleul.domain.routine.validator.RoutineValidator;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.enums.TaskStatus;
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
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoutineServiceTest {

    @Mock
    private RoutineRepository routineRepository;
    @Mock
    private CategoryFinder categoryFinder;
    @Mock
    private UserFinder userFinder;
    @Mock
    private RoutineValidator routineValidator;
    @Mock
    private RoutineTaskFactory routineTaskFactory;
    @Mock
    private RoutineFinder routineFinder;
    @Mock
    private RoutineFactory routineFactory;
    @Mock
    private RoutineCascadeDeleter routineCascadeDeleter;
    @Mock
    private TaskRepository taskRepository;

    private RoutineService routineService;

    @BeforeEach
    void setUp() {
        routineService = new RoutineService(
                routineRepository, categoryFinder, userFinder, routineValidator,
                routineTaskFactory, routineFinder, routineFactory, routineCascadeDeleter, taskRepository, null
        );
    }

    // ── 공통 픽스처 헬퍼 ──────────────────────────────────

    /**
     * RoutineResponse.from()이 goalCategory/generalCategory/repeatDays를 null 체크 없이
     * 바로 사용하기 때문에, 응답 변환을 거치는 모든 테스트는 반드시 이 헬퍼로 Routine mock을 만들어야 한다.
     * 순수 mock(Routine.class)만 쓰면 이 필드들이 null이라 RoutineResponse.from()에서 NPE가 발생한다.
     */
    private Routine routineMock() {
        Routine routine = mock(Routine.class);
        given(routine.getGoalCategory()).willReturn(mock(GoalCategory.class));
        given(routine.getGeneralCategory()).willReturn(mock(GeneralCategory.class));
        given(routine.getRepeatDays()).willReturn(List.of());
        return routine;
    }

    // ── create() ──────────────────────────────────────────

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("검증, 생성, 저장, Task 생성을 순서대로 수행한다")
        void create_orchestratesValidationCreationAndTaskGeneration() {
            // given
            User user = mock(User.class);
            GoalCategory goalCategory = mock(GoalCategory.class);
            GeneralCategory generalCategory = mock(GeneralCategory.class);
            Routine routine = mock(Routine.class); // 저장 전 routine은 응답 변환에 안 쓰이므로 스텁 불필요
            Routine savedRoutine = routineMock();  // ✅ 응답 변환 대상

            RoutineCreateRequest request = RoutineCreateRequest.builder()
                    .goalCategoryId(1L)
                    .generalCategoryId(2L)
                    .routineName("아침 운동")
                    .repeatStartDate(LocalDate.of(2026, 9, 1))
                    .repeatEndDate(LocalDate.of(2026, 9, 30))
                    .repeatDays(List.of(DayOfWeek.MONDAY))
                    .notificationEnabled(true)
                    .build();

            given(userFinder.findUser(100L)).willReturn(user);
            given(categoryFinder.findGoalCategory(1L, user)).willReturn(goalCategory);
            given(categoryFinder.findGeneralCategory(2L, user)).willReturn(generalCategory);
            given(routineFactory.create(user, goalCategory, generalCategory, request)).willReturn(routine);
            given(routineRepository.save(routine)).willReturn(savedRoutine);
            given(routineTaskFactory.generateTasks(savedRoutine)).willReturn(List.of());

            // when
            RoutineResponse response = routineService.create(request, 100L);

            // then
            assertThat(response).isNotNull();
            verify(routineValidator).validateCreate(request, user, generalCategory);
            verify(routineRepository).save(routine);
            verify(taskRepository).saveAll(List.of());
        }
    }

    // ── complete() ────────────────────────────────────────

    @Nested
    @DisplayName("complete()")
    class Complete {

        @Test
        @DisplayName("소유권을 확인한 루틴을 완료 처리한다")
        void complete_marksRoutineAsCompleted() {
            // given
            User user = mock(User.class);
            Routine routine = routineMock(); // ✅
            RoutineCompleteRequest request = RoutineCompleteRequest.builder()
                    .routineEndDate(LocalDate.of(2026, 9, 20))
                    .build();

            given(userFinder.findUser(100L)).willReturn(user);
            given(routineFinder.findByIdAndUser(10L, user)).willReturn(routine);

            // when
            routineService.complete(10L, 100L, request);

            // then
            verify(routine).complete(LocalDate.of(2026, 9, 20));
        }
    }

    // ── update() ──────────────────────────────────────────

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("goalCategoryId가 null이면 categoryFinder를 호출하지 않고 null로 update한다")
        void update_skipsGoalCategoryLookup_whenIdIsNull() {
            // given
            User user = mock(User.class);
            Routine routine = routineMock(); // ✅
            RoutineUpdateRequest request = RoutineUpdateRequest.builder()
                    .routineName("저녁 운동")
                    .build();

            given(userFinder.findUser(100L)).willReturn(user);
            given(routineFinder.findByIdAndUser(10L, user)).willReturn(routine);

            // when
            routineService.update(100L, 10L, request);

            // then
            verify(categoryFinder, never()).findGoalCategory(any(), any());
            verify(categoryFinder, never()).findGeneralCategory(any(), any());
            verify(routine).update(isNull(), isNull(), eq("저녁 운동"), isNull(), isNull(), isNull(), isNull(), isNull());
        }

        @Test
        @DisplayName("goalCategoryId가 있으면 categoryFinder로 조회해서 update에 반영한다")
        void update_looksUpGoalCategory_whenIdIsPresent() {
            // given
            User user = mock(User.class);
            Routine routine = routineMock();
            GoalCategory goalCategory = mock(GoalCategory.class);
            RoutineUpdateRequest request = RoutineUpdateRequest.builder()
                    .goalCategoryId(1L)
                    .build();

            given(userFinder.findUser(100L)).willReturn(user);
            given(routineFinder.findByIdAndUser(10L, user)).willReturn(routine);
            given(categoryFinder.findGoalCategory(1L, user)).willReturn(goalCategory);

            // when
            routineService.update(100L, 10L, request);

            // then
            verify(categoryFinder).findGoalCategory(1L, user);
            verify(routine).update(eq(goalCategory), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull());
        }

        @Test
        @DisplayName("generalCategoryId가 있으면 categoryFinder로 조회해서 update에 반영한다")
        void update_looksUpGeneralCategory_whenIdIsPresent() {
            // given
            User user = mock(User.class);
            Routine routine = routineMock();
            GeneralCategory generalCategory = mock(GeneralCategory.class);
            RoutineUpdateRequest request = RoutineUpdateRequest.builder()
                    .generalCategoryId(2L)
                    .build();

            given(userFinder.findUser(100L)).willReturn(user);
            given(routineFinder.findByIdAndUser(10L, user)).willReturn(routine);
            given(categoryFinder.findGeneralCategory(2L, user)).willReturn(generalCategory);

            // when
            routineService.update(100L, 10L, request);

            // then
            verify(categoryFinder).findGeneralCategory(2L, user);
            verify(routine).update(isNull(), eq(generalCategory), isNull(), isNull(), isNull(), isNull(), isNull(), isNull());
        }

        @Test
        @DisplayName("repeatDays가 있으면 updateRepeatDays를 호출한다")
        void update_callsUpdateRepeatDays_whenPresent() {
            // given
            User user = mock(User.class);
            Routine routine = routineMock();
            List<DayOfWeek> newDays = List.of(DayOfWeek.MONDAY);
            RoutineUpdateRequest request = RoutineUpdateRequest.builder()
                    .repeatDays(newDays)
                    .build();

            given(userFinder.findUser(100L)).willReturn(user);
            given(routineFinder.findByIdAndUser(10L, user)).willReturn(routine);

            // when
            routineService.update(100L, 10L, request);

            // then
            verify(routine).updateRepeatDays(newDays);
        }

        @Test
        @DisplayName("repeatDays가 null이면 updateRepeatDays를 호출하지 않는다")
        void update_skipsUpdateRepeatDays_whenNull() {
            // given
            User user = mock(User.class);
            Routine routine = routineMock();
            RoutineUpdateRequest request = RoutineUpdateRequest.builder().build();

            given(userFinder.findUser(100L)).willReturn(user);
            given(routineFinder.findByIdAndUser(10L, user)).willReturn(routine);
            // ✅ taskRepository.findByRoutine 스텁 제거 — repeatStartDate가 null이라 syncTasksWithRoutine에서 조기 리턴하므로 불필요

            // when
            routineService.update(100L, 10L, request);

            // then
            verify(routine, never()).updateRepeatDays(any());
        }

        @Test
        @DisplayName("notificationEnabled를 명시적으로 false로 보내면 그대로 update에 전달된다")
        void update_passesExplicitFalse_forNotificationEnabled() {
            // given
            User user = mock(User.class);
            Routine routine = routineMock();
            RoutineUpdateRequest request = RoutineUpdateRequest.builder()
                    .notificationEnabled(false)
                    .build();

            given(userFinder.findUser(100L)).willReturn(user);
            given(routineFinder.findByIdAndUser(10L, user)).willReturn(routine);
            // ✅ 이 줄 삭제: given(taskRepository.findByRoutine(routine)).willReturn(List.of());

            // when
            routineService.update(100L, 10L, request);

            // then
            verify(routine).update(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(false));
        }

        @Test
        @DisplayName("오늘 이후의 미완료 Task만 지우고, 완료 Task와 지난 날짜 Task는 남긴 채 오늘부터 재생성한다")
        void update_syncsTasks_keepsCompletedAndPastTasks() {
            // given — 오늘 기준으로 날짜를 잡는다 (update()는 한국 시간 오늘을 쓴다)
            LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
            User user = mock(User.class);
            Routine routine = routineMock(); // ✅
            given(routine.getRepeatStartDate()).willReturn(today.minusDays(10));
            given(routine.getRepeatEndDate()).willReturn(today.plusDays(20));

            RoutineUpdateRequest request = RoutineUpdateRequest.builder().build();

            Task completedTask = mock(Task.class);
            given(completedTask.getTaskStatus()).willReturn(TaskStatus.COMPLETED);
            given(completedTask.getRoutineDate()).willReturn(today.plusDays(5));

            Task futureIncomplete = mock(Task.class);
            given(futureIncomplete.getTaskStatus()).willReturn(TaskStatus.TODO);
            given(futureIncomplete.getRoutineDate()).willReturn(today.plusDays(3));

            Task pastMissed = mock(Task.class);
            given(pastMissed.getTaskStatus()).willReturn(TaskStatus.TODO);
            given(pastMissed.getRoutineDate()).willReturn(today.minusDays(2));

            given(userFinder.findUser(100L)).willReturn(user);
            given(routineFinder.findByIdAndUser(10L, user)).willReturn(routine);
            given(taskRepository.findByRoutine(routine)).willReturn(List.of(completedTask, futureIncomplete, pastMissed));
            Set<LocalDate> kept = Set.of(today.plusDays(5), today.minusDays(2));
            given(routineTaskFactory.generateTasks(routine, kept, today)).willReturn(List.of());

            // when
            routineService.update(100L, 10L, request);

            // then — 지난 놓친 Task(pastMissed)는 지우지 않는다
            verify(taskRepository).deleteAll(List.of(futureIncomplete));
            verify(routineTaskFactory).generateTasks(routine, kept, today);
        }

        @Test
        @DisplayName("repeatStartDate가 null이면 Task 동기화를 하지 않는다")
        void update_skipsTaskSync_whenStartDateIsNull() {
            // given
            User user = mock(User.class);
            Routine routine = routineMock(); // ✅
            given(routine.getRepeatStartDate()).willReturn(null);

            RoutineUpdateRequest request = RoutineUpdateRequest.builder().build();

            given(userFinder.findUser(100L)).willReturn(user);
            given(routineFinder.findByIdAndUser(10L, user)).willReturn(routine);

            // when
            routineService.update(100L, 10L, request);

            // then
            verifyNoInteractions(taskRepository);
        }
    }

    // ── delete() ──────────────────────────────────────────

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("소유권을 확인한 루틴에 대해 cascadeDeleter로 위임한다")
        void delete_delegatesToRoutineCascadeDeleter() {
            // given: delete()는 응답 변환(RoutineResponse.from)이 없으므로 순수 mock으로 충분
            User user = mock(User.class);
            Routine routine = mock(Routine.class);
            given(userFinder.findUser(100L)).willReturn(user);
            given(routineFinder.findByIdAndUser(10L, user)).willReturn(routine);

            // when
            routineService.delete(10L, 100L);

            // then
            verify(routineCascadeDeleter).deleteRoutineAndTasks(routine);
        }
    }

    // ── deleteAllByGeneralCategoryId() ───────────────────

    @Nested
    @DisplayName("deleteAllByGeneralCategoryId()")
    class DeleteAllByGeneralCategoryId {

        @Test
        @DisplayName("활성 루틴 전체에 대해 cascadeDeleter를 호출한다")
        void deleteAllByGeneralCategoryId_deletesEachActiveRoutine() {
            // given
            Routine routine1 = mock(Routine.class);
            Routine routine2 = mock(Routine.class);
            given(routineRepository.findAllActiveByGeneralCategoryId(1L))
                    .willReturn(List.of(routine1, routine2));

            // when
            routineService.deleteAllByGeneralCategoryId(1L);

            // then
            verify(routineCascadeDeleter).deleteRoutineAndTasks(routine1);
            verify(routineCascadeDeleter).deleteRoutineAndTasks(routine2);
        }

        @Test
        @DisplayName("활성 루틴이 없으면 cascadeDeleter를 호출하지 않는다")
        void deleteAllByGeneralCategoryId_doesNothing_whenNoActiveRoutines() {
            // given
            given(routineRepository.findAllActiveByGeneralCategoryId(1L)).willReturn(List.of());

            // when
            routineService.deleteAllByGeneralCategoryId(1L);

            // then
            verifyNoInteractions(routineCascadeDeleter);
        }
    }

    // ── syncGoalCategoryForGeneralCategory() ──────────────

    @Nested
    @DisplayName("syncGoalCategoryForGeneralCategory()")
    class SyncGoalCategoryForGeneralCategory {

        @Test
        @DisplayName("각 루틴과 하위 Task의 goalCategory를 재배정한다")
        void syncGoalCategoryForGeneralCategory_reassignsRoutineAndTasks() {
            // given: 응답 변환이 없으므로 순수 mock으로 충분
            GoalCategory newGoalCategory = mock(GoalCategory.class);
            Routine routine = mock(Routine.class);
            Task task = mock(Task.class);

            given(routineRepository.findAllActiveByGeneralCategoryId(1L)).willReturn(List.of(routine));
            given(taskRepository.findByRoutine(routine)).willReturn(List.of(task));

            // when
            routineService.syncGoalCategoryForGeneralCategory(1L, newGoalCategory);

            // then
            verify(routine).reassignGoalCategory(newGoalCategory);
            verify(task).reassignGoalCategory(newGoalCategory);
        }
    }
}