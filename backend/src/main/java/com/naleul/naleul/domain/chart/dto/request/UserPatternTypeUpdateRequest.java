package com.naleul.naleul.domain.chart.dto.request;

// domain/user/dto/request/UserPatternTypeUpdateRequest.java

import com.naleul.naleul.domain.user.enums.UserPatternType;
import jakarta.validation.constraints.NotNull;

public record UserPatternTypeUpdateRequest(
        @NotNull UserPatternType patternType
) {
}