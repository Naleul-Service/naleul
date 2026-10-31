package com.naleul.naleul.domain.mission.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MissionJoinRequest {

    @NotBlank(message = "자기소개는 필수입니다.")
    @Size(max = 500, message = "자기소개는 500자 이하여야 합니다.")
    private String introduction;
}