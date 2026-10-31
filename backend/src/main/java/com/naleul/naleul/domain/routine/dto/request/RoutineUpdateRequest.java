package com.naleul.naleul.domain.routine.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoutineUpdateRequest {
    private Long goalCategoryId;
    private Long generalCategoryId;
    private Boolean notificationEnabled;

    private String routineName;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate repeatStartDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate repeatEndDate;

    private List<DayOfWeek> repeatDays;
    private LocalTime repeatStartTime;
    private LocalTime repeatEndTime;

    /** 루틴 방법 (예: "스쿼트 4x10, 런지 3x12"). null 이면 그대로, 빈 문자열이면 지움. 최대 300자 */
    @Size(max = 300, message = "루틴 설명은 300자 이하로 입력해주세요.")
    private String description;

    /**
     * 요일별 시간 (V41, 선택) — 이 요일만 기본 시간(repeatStartTime/EndTime)과 다르게.
     * 예) 월·수·금 루틴 기본 20:00~20:25 + [{ "dayOfWeek": "WEDNESDAY", "startTime": "07:00", "endTime": "07:25" }]
     *
     * 수정: null 이면 그대로, 빈 목록이면 모든 요일을 기본 시간으로, 목록이면 그 요일만 덮어쓰기 (나머지는 기본 시간)
     */
    private List<RoutineDayTime> dayTimes;
}
