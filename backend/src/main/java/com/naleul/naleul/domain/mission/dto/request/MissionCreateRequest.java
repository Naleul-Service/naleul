package com.naleul.naleul.domain.mission.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.naleul.naleul.domain.mission.enums.MissionCategory;
import com.naleul.naleul.domain.mission.enums.MissionCategoryItem;
import com.naleul.naleul.domain.mission.enums.MissionDuration;
import com.naleul.naleul.domain.mission.enums.RepeatDayType;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@NoArgsConstructor
public class MissionCreateRequest {

    @NotBlank(message = "미션 제목은 필수입니다.")
    @Size(max = 100, message = "미션 제목은 100자 이하여야 합니다.")
    private String title;

    @NotNull(message = "제한 인원은 필수입니다.")
    @Min(value = 1, message = "최소 1명 이상이어야 합니다.")
    @Max(value = 4, message = "최대 4명까지 가능합니다.")
    private Integer maxParticipants;

    @NotNull(message = "공개 여부는 필수입니다.")
    private Boolean isPrivate;

    private String password; // isPrivate=true 일 때만 필수 → Service에서 커스텀 검증

    @NotNull(message = "카테고리는 필수입니다.")
    private MissionCategory category;

    private MissionCategoryItem categoryItem; // category != ETC 일 때 필수 → Service에서 검증

    @Size(max = 10, message = "카테고리 기타 입력은 10자 이하여야 합니다.")
    private String customCategoryName; // category == ETC 일 때만 사용

    @Size(max = 10, message = "항목 기타 입력은 10자 이하여야 합니다.")
    private String customItemName; // categoryItem == ETC 일 때만 사용

    @NotBlank(message = "미션 조건은 필수입니다.")
    private String missionCondition;

    @NotNull(message = "모집 시작일은 필수입니다.")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate recruitStartDate;

    @NotNull(message = "모집 종료일은 필수입니다.")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate recruitEndDate;

    @NotNull(message = "진행 기간은 필수입니다.")
    private MissionDuration duration;

    // 성공/실패 조건: 기존 @NotBlank 필수 → 선택으로 변경 (어노테이션 제거)
    private String successCondition;
    private String failCondition;

    private String participationCondition; // 선택

    @NotNull(message = "반복 요일 설정은 필수입니다.") // 기존 선택 → 필수로 변경
    private RepeatDayType repeatDayType;

    private List<DayOfWeek> repeatDays; // repeatDayType == CUSTOM 일 때만 사용

    @JsonFormat(pattern = "HH:mm")
    private LocalTime startTime; // 선택

    @JsonFormat(pattern = "HH:mm")
    private LocalTime endTime;   // 선택
}