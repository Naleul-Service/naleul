package com.naleul.naleul.domain.routine.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoutineCreateRequest {

    @NotNull(message = "목표 카테고리 ID는 필수입니다.")
    private Long goalCategoryId;

    /** 영역(세부 목표). 비우면 목표의 "기타 할 일"에 들어간다 */
    private Long generalCategoryId;

    @NotNull(message = "루틴 이름은 필수입니다.")
    private String routineName;

    @NotNull(message = "시작 날짜는 필수입니다.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate repeatStartDate;

    @NotNull(message = "종료 날짜는 필수입니다.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate repeatEndDate;

    @Builder.Default
    private boolean notificationEnabled = true;

    @Builder.Default
    private List<DayOfWeek> repeatDays = new ArrayList<>(); // null 대신 빈 리스트
    private LocalTime repeatStartTime;
    private LocalTime repeatEndTime;

    /** 하는 방법 (선택, 최대 300자) — 예: "스쿼트 4x10, 런지 3x12" */
    @Size(max = 300, message = "루틴 설명은 300자 이하로 입력해주세요.")
    private String description;

    /**
     * 요일별 시간 (V41, 선택) — 이 요일만 기본 시간(repeatStartTime/EndTime)과 다르게.
     * 예) 월·수·금 루틴 기본 20:00~20:25 + [{ "dayOfWeek": "WEDNESDAY", "startTime": "07:00", "endTime": "07:25" }]
     *
     */
    private List<RoutineDayTime> dayTimes;
}
