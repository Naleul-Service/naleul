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
public class MissionUpdateRequest {

    // ⚠️ 수정 API에서는 무시됩니다 — MissionService.updateMission()이 기존 값을 그대로 유지해요.
    // (생성 시에만 결정되고, 모집중에도 바꿀 수 없는 필드: 제목/인원/카테고리)
    // 필드 자체는 남겨뒀어요 — MissionCreateRequest와 프론트 모델을 공유하는 구조라
    // 여기서 지우면 iOS 쪽 DTO 분기가 더 복잡해질 수 있어서요.
    @NotBlank(message = "미션 제목은 필수입니다.")
    @Size(max = 100, message = "미션 제목은 100자 이하여야 합니다.")
    private String title;

    // ⚠️ 수정 API에서는 무시됩니다 — 위와 동일한 이유
    @NotNull(message = "제한 인원은 필수입니다.")
    @Min(value = 1, message = "최소 1명 이상이어야 합니다.")
    @Max(value = 4, message = "최대 4명까지 가능합니다.")
    private Integer maxParticipants;

    @NotNull(message = "공개 여부는 필수입니다.")
    private Boolean isPrivate;

    private String password;

    // ⚠️ 수정 API에서는 무시됩니다 — 위와 동일한 이유
    @NotNull(message = "카테고리는 필수입니다.")
    private MissionCategory category;

    // ⚠️ 수정 API에서는 무시됩니다
    private MissionCategoryItem categoryItem;

    // ⚠️ 수정 API에서는 무시됩니다
    @Size(max = 10, message = "카테고리 기타 입력은 10자 이하여야 합니다.")
    private String customCategoryName;

    // ⚠️ 수정 API에서는 무시됩니다
    @Size(max = 10, message = "항목 기타 입력은 10자 이하여야 합니다.")
    private String customItemName;

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

    private String successCondition;
    private String failCondition;

    private String participationCondition;

    @NotNull(message = "반복 요일 설정은 필수입니다.")
    private RepeatDayType repeatDayType;

    private List<DayOfWeek> repeatDays;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime startTime;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime endTime;
}