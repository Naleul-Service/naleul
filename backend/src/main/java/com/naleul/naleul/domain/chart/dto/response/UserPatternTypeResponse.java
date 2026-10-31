package com.naleul.naleul.domain.chart.dto.response;

// domain/user/dto/response/UserPatternTypeResponse.java
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.enums.UserPatternType;

public record UserPatternTypeResponse(
        Long userId,
        UserPatternType patternType
) {
    public static UserPatternTypeResponse from(User user) {
        return new UserPatternTypeResponse(user.getUserId(), user.getPatternType());
    }
}