// domain/user/dto/response/UserNicknameUpdateResponse.java
package com.naleul.naleul.domain.user.dto.response;

import com.naleul.naleul.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserNicknameUpdateResponse {
    private Long userId;
    private String nickname;

    public static UserNicknameUpdateResponse from(User user) {
        return UserNicknameUpdateResponse.builder()
                .userId(user.getUserId())
                .nickname(user.getUserName())
                .build();
    }
}