package com.naleul.naleul.domain.mission.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MissionPasswordCheckRequest {

    @NotBlank(message = "비밀번호를 입력해주세요.")
    private String password;
}
