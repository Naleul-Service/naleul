// retrospective/dto/response/RetrospectiveResponse.java
package com.naleul.naleul.domain.retrospective.dto.response;

import com.naleul.naleul.domain.retrospective.entity.Retrospective;
import com.naleul.naleul.domain.retrospective.enums.ReviewType;
import lombok.Getter;

import java.time.LocalDate;

@Getter
public class RetrospectiveResponse {

    private final Long retrospectiveId;
    private final ReviewType reviewType;
    private final LocalDate reviewDate;
    private final String content;

    // ✅ goalCategory는 이제 항상 존재하므로 null 체크 불필요 (더 이상 nullable이 아님)
    private final Long goalCategoryId;
    private final String goalCategoryName;
    private final String goalCategoryColorCode;

    // generalCategory는 여전히 선택사항이므로 null 체크 유지
    private final Long generalCategoryId;
    private final String generalCategoryName;
    private final String generalCategoryColorCode;

    private RetrospectiveResponse(Retrospective retrospective) {
        this.retrospectiveId = retrospective.getRetrospectiveId();
        this.reviewType = retrospective.getReviewType();
        this.reviewDate = retrospective.getReviewDate();
        this.content = retrospective.getContent();

        // ✅ goalCategory는 이제 항상 있으므로 바로 꺼내와도 안전 (null 체크 삭제)
        this.goalCategoryId = retrospective.getGoalCategory().getGoalCategoryId();
        this.goalCategoryName = retrospective.getGoalCategory().getGoalCategoryName();
        this.goalCategoryColorCode = retrospective.getGoalCategory().getUserColor() != null
                ? retrospective.getGoalCategory().getUserColor().getColorCode() : null;

        // generalCategory는 여전히 nullable이므로 null 체크 그대로 유지
        this.generalCategoryId = retrospective.getGeneralCategory() != null
                ? retrospective.getGeneralCategory().getGeneralCategoryId() : null;
        this.generalCategoryName = retrospective.getGeneralCategory() != null
                ? retrospective.getGeneralCategory().getGeneralCategoryName() : null;
        this.generalCategoryColorCode = retrospective.getGeneralCategory() != null
                && retrospective.getGeneralCategory().getColor() != null
                ? retrospective.getGeneralCategory().getColor().getColorCode() : null;
    }

    public static RetrospectiveResponse from(Retrospective retrospective) {
        return new RetrospectiveResponse(retrospective);
    }
}