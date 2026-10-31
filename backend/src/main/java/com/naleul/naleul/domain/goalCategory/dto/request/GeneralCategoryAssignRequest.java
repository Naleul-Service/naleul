package com.naleul.naleul.domain.goalCategory.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class GeneralCategoryAssignRequest {
    private List<Long> generalCategoryIds;
}
