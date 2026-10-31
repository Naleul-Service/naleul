package com.naleul.naleul.domain.mission.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

// MissionPasswordCheckResponse.java
@Getter
@AllArgsConstructor
public class MissionPasswordCheckResponse {
    private Boolean isValid;

    public static MissionPasswordCheckResponse of(boolean isValid) {
        return new MissionPasswordCheckResponse(isValid);
    }
}