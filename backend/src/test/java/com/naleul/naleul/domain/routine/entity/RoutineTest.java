package com.naleul.naleul.domain.routine.entity;

import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.routine.enums.RoutineStatus;
import com.naleul.naleul.domain.routineRepeatDay.entity.RoutineRepeatDay;
import com.naleul.naleul.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RoutineTest {

    // ── 공통 픽스처 헬퍼 ──────────────────────────────────

    private Routine createRoutine() {
        return Routine.create(
                mock(User.class), mock(GoalCategory.class), mock(GeneralCategory.class),
                "아침 운동",
                LocalTime.of(7, 0), LocalTime.of(8, 0),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                List.of(DayOfWeek.MONDAY),
                true
        );
    }

    // ── create() ──────────────────────────────────────────

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("시작일이 오늘보다 미래면 NOT_STARTED로 생성된다")
        void create_withFutureStartDate_isNotStarted() {
            Routine routine = Routine.create(
                    mock(User.class), mock(GoalCategory.class), mock(GeneralCategory.class),
                    "아침 운동",
                    LocalTime.of(7, 0), LocalTime.of(8, 0),
                    LocalDate.now().plusDays(3), LocalDate.now().plusDays(30),
                    List.of(DayOfWeek.MONDAY),
                    true
            );

            assertThat(routine.getRoutineStatus()).isEqualTo(RoutineStatus.NOT_STARTED);
        }

        @Test
        @DisplayName("시작일이 오늘이면 IN_PROGRESS로 생성된다")
        void create_withTodayAsStartDate_isInProgress() {
            Routine routine = Routine.create(
                    mock(User.class), mock(GoalCategory.class), mock(GeneralCategory.class),
                    "아침 운동",
                    LocalTime.of(7, 0), LocalTime.of(8, 0),
                    LocalDate.now(), LocalDate.now().plusDays(30),
                    List.of(DayOfWeek.MONDAY),
                    true
            );

            assertThat(routine.getRoutineStatus()).isEqualTo(RoutineStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("시작일이 오늘보다 과거면 IN_PROGRESS로 생성된다")
        void create_withPastStartDate_isInProgress() {
            Routine routine = Routine.create(
                    mock(User.class), mock(GoalCategory.class), mock(GeneralCategory.class),
                    "아침 운동",
                    LocalTime.of(7, 0), LocalTime.of(8, 0),
                    LocalDate.now().minusDays(5), LocalDate.now().plusDays(30),
                    List.of(DayOfWeek.MONDAY),
                    true
            );

            assertThat(routine.getRoutineStatus()).isEqualTo(RoutineStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("요일이 주어지면 전달받은 요일들로 반복요일 목록이 채워진다")
        void create_buildsRepeatDaysFromDayOfWeeks() {
            // when
            Routine routine = Routine.create(
                    mock(User.class), mock(GoalCategory.class), mock(GeneralCategory.class),
                    "아침 운동",
                    LocalTime.of(7, 0), LocalTime.of(8, 0),
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                    List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                    true
            );

            // then
            assertThat(routine.getRepeatDays())
                    .extracting(RoutineRepeatDay::getDayOfWeek)
                    .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
        }

        @Test
        @DisplayName("요일이 null이면 반복요일 목록이 빈 리스트가 된다")
        void create_withNullDayOfWeeks_resultsInEmptyRepeatDays() {
            // when
            Routine routine = Routine.create(
                    mock(User.class), mock(GoalCategory.class), mock(GeneralCategory.class),
                    "아침 운동",
                    LocalTime.of(7, 0), LocalTime.of(8, 0),
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                    null,
                    true
            );

            // then
            assertThat(routine.getRepeatDays()).isEmpty();
        }

        @Test
        @DisplayName("요일이 빈 리스트면 반복요일 목록도 빈 리스트가 된다")
        void create_withEmptyDayOfWeeks_resultsInEmptyRepeatDays() {
            // when
            Routine routine = Routine.create(
                    mock(User.class), mock(GoalCategory.class), mock(GeneralCategory.class),
                    "아침 운동",
                    LocalTime.of(7, 0), LocalTime.of(8, 0),
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                    List.of(),
                    true
            );

            // then
            assertThat(routine.getRepeatDays()).isEmpty();
        }
    }

    @Nested
    @DisplayName("start()")
    class Start {

        @Test
        @DisplayName("NOT_STARTED 상태면 IN_PROGRESS로 전환된다")
        void start_transitionsToInProgress_whenNotStarted() {
            Routine routine = createRoutine(); // repeatStartDate가 미래라 NOT_STARTED로 생성된다고 가정

            routine.start();

            assertThat(routine.getRoutineStatus()).isEqualTo(RoutineStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("이미 IN_PROGRESS면 아무 일도 일어나지 않는다")
        void start_doesNothing_whenAlreadyInProgress() {
            Routine routine = createRoutine();
            routine.start(); // 먼저 한 번 전환

            routine.start(); // 다시 호출해도

            assertThat(routine.getRoutineStatus()).isEqualTo(RoutineStatus.IN_PROGRESS); // 그대로
        }

        @Test
        @DisplayName("COMPLETED 상태면 IN_PROGRESS로 되돌아가지 않는다")
        void start_doesNotRevert_whenAlreadyCompleted() {
            Routine routine = createRoutine();
            routine.complete(LocalDate.now());

            routine.start();

            assertThat(routine.getRoutineStatus()).isEqualTo(RoutineStatus.COMPLETED);
        }
    }

    // ── update() — 부분 수정 ──────────────────────────────

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("routineName만 수정하면 나머지 필드는 기존 값을 유지한다")
        void update_onlyRoutineName_keepsOtherFieldsUnchanged() {
            // given
            Routine routine = createRoutine();
            GoalCategory originalGoalCategory = routine.getGoalCategory();
            GeneralCategory originalGeneralCategory = routine.getGeneralCategory();

            // when
            routine.update(null, null,
                    "저녁 운동",
                    null, null, null, null,
                    null);

            // then
            assertThat(routine.getRoutineName()).isEqualTo("저녁 운동");
            assertThat(routine.getGoalCategory()).isEqualTo(originalGoalCategory);
            assertThat(routine.getGeneralCategory()).isEqualTo(originalGeneralCategory);
            assertThat(routine.getRepeatStartDate()).isEqualTo(LocalDate.of(2026, 9, 1));
            assertThat(routine.getRepeatEndDate()).isEqualTo(LocalDate.of(2026, 9, 30));
            assertThat(routine.getRepeatStartTime()).isEqualTo(LocalTime.of(7, 0));
            assertThat(routine.getRepeatEndTime()).isEqualTo(LocalTime.of(8, 0));
            assertThat(routine.isNotificationEnabled()).isTrue();
        }

        @Test
        @DisplayName("겹치는 요일이 있으면 유니크 제약 위반 없이 차이만 반영된다")
        void updateRepeatDays_withOverlappingDays_onlyChangesDifference() {
            Routine routine = Routine.create(
                    mock(User.class), mock(GoalCategory.class), mock(GeneralCategory.class),
                    "테스트 루틴", LocalTime.of(7, 0), LocalTime.of(8, 0),
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                    List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                    true
            );

            routine.updateRepeatDays(List.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY));

            assertThat(routine.getRepeatDays())
                    .extracting(RoutineRepeatDay::getDayOfWeek)
                    .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
        }

        @Test
        @DisplayName("goalCategory만 수정하면 generalCategory 등 나머지는 유지된다")
        void update_onlyGoalCategory_keepsOtherFieldsUnchanged() {
            // given
            Routine routine = createRoutine();
            GeneralCategory originalGeneralCategory = routine.getGeneralCategory();
            GoalCategory newGoalCategory = mock(GoalCategory.class);

            // when
            routine.update(newGoalCategory, null, null, null, null, null, null, null);

            // then
            assertThat(routine.getGoalCategory()).isEqualTo(newGoalCategory);
            assertThat(routine.getGeneralCategory()).isEqualTo(originalGeneralCategory);
            assertThat(routine.getRoutineName()).isEqualTo("아침 운동");
        }

        @Test
        @DisplayName("repeatStartDate만 수정하면 repeatEndDate 등 나머지는 유지된다")
        void update_onlyStartDate_keepsOtherFieldsUnchanged() {
            // given
            Routine routine = createRoutine();

            // when
            routine.update(null, null, null,
                    LocalDate.of(2026, 9, 5), null, null, null,
                    null);

            // then
            assertThat(routine.getRepeatStartDate()).isEqualTo(LocalDate.of(2026, 9, 5));
            assertThat(routine.getRepeatEndDate()).isEqualTo(LocalDate.of(2026, 9, 30));
            assertThat(routine.getRoutineName()).isEqualTo("아침 운동");
        }

        @Test
        @DisplayName("repeatEndDate만 수정하면 repeatStartDate 등 나머지는 유지된다")
        void update_onlyEndDate_keepsOtherFieldsUnchanged() {
            // given
            Routine routine = createRoutine();

            // when
            routine.update(null, null, null,
                    null, LocalDate.of(2026, 10, 5), null, null,
                    null);

            // then
            assertThat(routine.getRepeatEndDate()).isEqualTo(LocalDate.of(2026, 10, 5));
            assertThat(routine.getRepeatStartDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        }

        @Test
        @DisplayName("repeatStartTime만 수정하면 repeatEndTime 등 나머지는 유지된다")
        void update_onlyStartTime_keepsOtherFieldsUnchanged() {
            // given
            Routine routine = createRoutine();

            // when
            routine.update(null, null, null, null, null,
                    LocalTime.of(6, 0), null,
                    null);

            // then
            assertThat(routine.getRepeatStartTime()).isEqualTo(LocalTime.of(6, 0));
            assertThat(routine.getRepeatEndTime()).isEqualTo(LocalTime.of(8, 0));
        }

        @Test
        @DisplayName("repeatEndTime만 수정하면 repeatStartTime 등 나머지는 유지된다")
        void update_onlyEndTime_keepsOtherFieldsUnchanged() {
            // given
            Routine routine = createRoutine();

            // when
            routine.update(null, null, null, null, null, null,
                    LocalTime.of(9, 0),
                    null);

            // then
            assertThat(routine.getRepeatEndTime()).isEqualTo(LocalTime.of(9, 0));
            assertThat(routine.getRepeatStartTime()).isEqualTo(LocalTime.of(7, 0));
        }

        @Test
        @DisplayName("notificationEnabled를 명시적으로 false로 보내면 실제 반영된다 (null과 구분)")
        void update_explicitFalse_forNotificationEnabled_isApplied() {
            // given: notificationEnabled = true로 생성됨
            Routine routine = createRoutine();

            // when
            routine.update(null, null, null, null, null, null, null, false);

            // then
            assertThat(routine.isNotificationEnabled()).isFalse();
        }

        @Test
        @DisplayName("notificationEnabled가 null이면 기존 값을 유지한다")
        void update_nullNotificationEnabled_keepsExistingValue() {
            // given
            Routine routine = createRoutine();

            // when
            routine.update(null, null, "저녁 운동", null, null, null, null, null);

            // then
            assertThat(routine.isNotificationEnabled()).isTrue();
        }

        @Test
        @DisplayName("모든 필드가 null이면 아무것도 바뀌지 않는다")
        void update_allNull_changesNothing() {
            // given
            Routine routine = createRoutine();
            GoalCategory originalGoalCategory = routine.getGoalCategory();
            GeneralCategory originalGeneralCategory = routine.getGeneralCategory();

            // when
            routine.update(null, null, null, null, null, null, null, null);

            // then
            assertThat(routine.getGoalCategory()).isEqualTo(originalGoalCategory);
            assertThat(routine.getGeneralCategory()).isEqualTo(originalGeneralCategory);
            assertThat(routine.getRoutineName()).isEqualTo("아침 운동");
            assertThat(routine.getRepeatStartDate()).isEqualTo(LocalDate.of(2026, 9, 1));
            assertThat(routine.getRepeatEndDate()).isEqualTo(LocalDate.of(2026, 9, 30));
            assertThat(routine.getRepeatStartTime()).isEqualTo(LocalTime.of(7, 0));
            assertThat(routine.getRepeatEndTime()).isEqualTo(LocalTime.of(8, 0));
            assertThat(routine.isNotificationEnabled()).isTrue();
        }

        @Test
        @DisplayName("모든 필드를 동시에 수정하면 전부 반영된다")
        void update_allFields_appliesAllChanges() {
            // given
            Routine routine = createRoutine();
            GoalCategory newGoalCategory = mock(GoalCategory.class);
            GeneralCategory newGeneralCategory = mock(GeneralCategory.class);

            // when
            routine.update(
                    newGoalCategory, newGeneralCategory,
                    "저녁 운동",
                    LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                    LocalTime.of(18, 0), LocalTime.of(19, 0),
                    false
            );

            // then
            assertThat(routine.getGoalCategory()).isEqualTo(newGoalCategory);
            assertThat(routine.getGeneralCategory()).isEqualTo(newGeneralCategory);
            assertThat(routine.getRoutineName()).isEqualTo("저녁 운동");
            assertThat(routine.getRepeatStartDate()).isEqualTo(LocalDate.of(2026, 10, 1));
            assertThat(routine.getRepeatEndDate()).isEqualTo(LocalDate.of(2026, 10, 31));
            assertThat(routine.getRepeatStartTime()).isEqualTo(LocalTime.of(18, 0));
            assertThat(routine.getRepeatEndTime()).isEqualTo(LocalTime.of(19, 0));
            assertThat(routine.isNotificationEnabled()).isFalse();
        }
    }

    // ── updateRepeatDays() ────────────────────────────────

    @Nested
    @DisplayName("updateRepeatDays()")
    class UpdateRepeatDays {

        @Test
        @DisplayName("기존 반복 요일 List를 새 목록으로 완전히 교체한다")
        void updateRepeatDays_replacesExistingDays() {
            // given: 기존은 MONDAY
            Routine routine = createRoutine();

            // when
            routine.updateRepeatDays(List.of(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY));

            // then
            assertThat(routine.getRepeatDays())
                    .extracting(RoutineRepeatDay::getDayOfWeek)
                    .containsExactlyInAnyOrder(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY);
        }

        @Test
        @DisplayName("빈 리스트로 교체하면 반복 요일 List가 전부 비워진다 (매일 반복 취급의 전제)")
        void updateRepeatDays_withEmptyList_clearsAllDays() {
            // given
            Routine routine = createRoutine();

            // when
            routine.updateRepeatDays(List.of());

            // then
            assertThat(routine.getRepeatDays()).isEmpty();
        }
    }

    // ── complete() ────────────────────────────────────────

    @Nested
    @DisplayName("complete()")
    class Complete {

        @Test
        @DisplayName("상태를 COMPLETED로 바꾸고 actualEndDate를 전달받은 값으로 설정한다")
        void complete_setsCompletedAndActualEndDate() {
            // given
            Routine routine = createRoutine();

            // when
            routine.complete(LocalDate.of(2026, 9, 20));

            // then
            assertThat(routine.getRoutineStatus()).isEqualTo(RoutineStatus.COMPLETED);
            assertThat(routine.getActualEndDate()).isEqualTo(LocalDate.of(2026, 9, 20));
        }

        @Test
        @DisplayName("repeatEndDate 이전 날짜로도 완료 처리할 수 있다 (조기 완료)")
        void complete_allowsEarlyCompletion() {
            // given: repeatEndDate는 2026-09-30
            Routine routine = createRoutine();

            // when: 그보다 이른 날짜로 완료 처리
            routine.complete(LocalDate.of(2026, 9, 10));

            // then
            assertThat(routine.getRoutineStatus()).isEqualTo(RoutineStatus.COMPLETED);
            assertThat(routine.getActualEndDate()).isEqualTo(LocalDate.of(2026, 9, 10));
        }
    }

    // ── expire() ──────────────────────────────────────────

    @Nested
    @DisplayName("expire()")
    class Expire {

        @Test
        @DisplayName("NOT_STARTED 상태면 COMPLETED로 전환되고 actualEndDate가 repeatEndDate로 채워진다")
        void expire_transitionsToCompleted_whenNotStarted() {
            // given: create() 직후 상태는 NOT_STARTED
            Routine routine = createRoutine();

            // when
            routine.expire();

            // then
            assertThat(routine.getRoutineStatus()).isEqualTo(RoutineStatus.COMPLETED);
            assertThat(routine.getActualEndDate()).isEqualTo(routine.getRepeatEndDate());
        }

        @Test
        @DisplayName("이미 COMPLETED면 만료 처리해도 actualEndDate가 덮어써지지 않는다")
        void expire_doesNotOverwrite_whenAlreadyCompleted() {
            // given: 유저가 이미 명시적으로 완료 처리한 상태
            Routine routine = createRoutine();
            routine.complete(LocalDate.of(2026, 9, 15));

            // when
            routine.expire();

            // then
            assertThat(routine.getRoutineStatus()).isEqualTo(RoutineStatus.COMPLETED);
            assertThat(routine.getActualEndDate()).isEqualTo(LocalDate.of(2026, 9, 15)); // repeatEndDate(9/30)로 안 바뀜
        }
    }

    // ── delete() ──────────────────────────────────────────

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("상태를 DELETED로 변경한다 (소프트 삭제)")
        void delete_setsStatusToDeleted() {
            // given
            Routine routine = createRoutine();

            // when
            routine.delete();

            // then
            assertThat(routine.getRoutineStatus()).isEqualTo(RoutineStatus.DELETED);
        }
    }

    // ── reassignGoalCategory() ────────────────────────────

    @Nested
    @DisplayName("reassignGoalCategory()")
    class ReassignGoalCategory {

        @Test
        @DisplayName("goalCategory만 교체되고 다른 필드는 영향받지 않는다")
        void reassignGoalCategory_changesOnlyGoalCategory() {
            // given
            Routine routine = createRoutine();
            GeneralCategory originalGeneralCategory = routine.getGeneralCategory();
            GoalCategory newGoalCategory = mock(GoalCategory.class);

            // when
            routine.reassignGoalCategory(newGoalCategory);

            // then
            assertThat(routine.getGoalCategory()).isEqualTo(newGoalCategory);
            assertThat(routine.getGeneralCategory()).isEqualTo(originalGeneralCategory);
        }
    }
}