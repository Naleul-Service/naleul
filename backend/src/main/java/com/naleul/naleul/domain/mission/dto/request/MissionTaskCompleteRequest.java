package com.naleul.naleul.domain.mission.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class MissionTaskCompleteRequest {

    @NotBlank(message = "인증 사진 URL은 필수입니다.")
    private String imageUrl;

    private String comment; // 한마디 (선택)

    // ✅ 추가 (선택 입력)
    private LocalDateTime actualStartAt;
    private LocalDateTime actualEndAt;
}