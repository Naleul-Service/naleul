package com.naleul.naleul.domain.notification.nudge;

import com.naleul.naleul.domain.routine.util.RoutinePracticeStats.Stats;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RoutineNudgeMessagesTest {

    private static Stats stats(int total, int done, int streak) {
        return new Stats(total, done, streak, Map.of());
    }

    @Test
    void 연속이_2일_이상이면_연속_기록으로() {
        var m = RoutineNudgeMessages.yesterdayTime("'📖 독서'", "10:00", stats(10, 8, 7));
        assertThat(m.body()).isEqualTo("어제 10:00에 '📖 독서'를 했어요. 연속 7일 기록을 지켜봐요");
    }

    @Test
    void 연속이_짧으면_실천률로() {
        var m = RoutineNudgeMessages.yesterdayTime("'운동'", "07:30", stats(10, 4, 1));
        assertThat(m.body()).isEqualTo("어제 07:30에 '운동'을 했어요. 실천률 40% — 오늘도 하면 45%로 올라가요");
    }

    @Test
    void 시작_알림은_가장_높은_루틴이면_그렇게_말한다() {
        var top = RoutineNudgeMessages.routineStart("'독서'", stats(10, 9, 5), true);
        assertThat(top.body()).isEqualTo("지금은 실천률이 90%로 가장 높은 '독서' 루틴을 할 시간이에요. 실천률을 지켜봐요");
        var other = RoutineNudgeMessages.routineStart("'독서'", stats(10, 5, 1), false);
        assertThat(other.body()).isEqualTo("지금은 '독서' 루틴을 할 시간이에요. 실천률 50% → 오늘 하면 55%");
        var fresh = RoutineNudgeMessages.routineStart("'독서'", stats(1, 1, 1), false);
        assertThat(fresh.body()).isEqualTo("지금은 '독서' 루틴을 할 시간이에요");
    }

    @Test
    void 실행_창은_직전_5분() {
        assertThat(RoutineNudgeScheduler.dueNow(600, 600)).isTrue();
        assertThat(RoutineNudgeScheduler.dueNow(596, 600)).isTrue();
        assertThat(RoutineNudgeScheduler.dueNow(595, 600)).isFalse();
        assertThat(RoutineNudgeScheduler.dueNow(601, 600)).isFalse();
    }
}
