package com.naleul.naleul.domain.routine.service;

import com.naleul.naleul.domain.routine.entity.Routine;
import com.naleul.naleul.domain.routine.repository.RoutineRepository;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class RoutineFinderTest {

    @Mock
    private RoutineRepository routineRepository;

    private RoutineFinder routineFinder;

    @BeforeEach
    void setUp() {
        routineFinder = new RoutineFinder(routineRepository);
    }

    @Nested
    @DisplayName("findByIdAndUser()")
    class FindByIdAndUser {

        @Test
        @DisplayName("소유권이 확인된 루틴이 존재하면 반환한다")
        void returnsRoutine_whenOwnedByUser() {
            // given
            User user = mock(User.class);
            Routine routine = mock(Routine.class);
            given(routineRepository.findByRoutineIdAndUser(10L, user)).willReturn(Optional.of(routine));

            // when
            Routine result = routineFinder.findByIdAndUser(10L, user);

            // then
            assertThat(result).isEqualTo(routine);
        }

        @Test
        @DisplayName("해당 유저 소유의 루틴이 존재하지 않으면 ROUTINE_NOT_FOUND 예외가 발생한다")
        void throwsException_whenNotFoundOrNotOwned() {
            // given
            User user = mock(User.class);
            given(routineRepository.findByRoutineIdAndUser(10L, user)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> routineFinder.findByIdAndUser(10L, user))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ROUTINE_NOT_FOUND);
        }
    }
}