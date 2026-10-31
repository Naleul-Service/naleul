package com.naleul.naleul.domain.userColor.dto.response;

import com.naleul.naleul.domain.userColor.entity.UserColor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserColorResponse {
    private Long userColorId;
    private String colorCode;
    private boolean isDefault;

    public static UserColorResponse from(UserColor userColor) {
        return UserColorResponse.builder()
                .userColorId(userColor.getUserColorId())
                .colorCode(userColor.getColorCode())
                .isDefault(userColor.isDefault())
                .build();
    }
}