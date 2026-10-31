package com.naleul.naleul.domain.pattern.calculator;

import com.naleul.naleul.domain.pattern.enums.DayGroup;

import java.time.DayOfWeek;
import java.util.List;

/** 잘 지키는 루틴 / 자주 놓치는 블록 (명세 4 · 2-7) */
public record RoutineRankStats(List<Kept> kept, List<Missed> missed) {

    public record Kept(Long routineId, String title, String emoji, int rate, int samples) {
    }

    /**
     * 자주 놓치는 블록. 요일 기준이면 dayOfWeek, 시간 기준이면 dayGroup + hour 가 채워진다.
     * @param label       "근력 운동 (목)", "토익 RC (평일 08시)"
     * @param description "목요일에 자주 놓쳐요", "출근 준비와 가까운 시간", "평일 08시 블록"
     */
    public record Missed(Long routineId, String routineName, String emoji, String label, String description,
                         int rate, int samples, DayOfWeek dayOfWeek, DayGroup dayGroup, Integer hour) {
    }
}
