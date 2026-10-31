package com.naleul.naleul.domain.ideaBoard.dto.response;

import com.naleul.naleul.domain.ideaBoard.entity.IdeaBoard;
import com.naleul.naleul.domain.ideaBoard.enums.IdeaStatus;

import java.time.LocalDateTime;

public record IdeaBoardResponse(
        Long ideaBoardId,
        Long goalCategoryId,   // ⭐ 추가
        String idea,
        String imageUrl,
        IdeaStatus ideaStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static IdeaBoardResponse from(IdeaBoard ideaBoard) {
        return new IdeaBoardResponse(
                ideaBoard.getIdeaBoardId(),
                ideaBoard.getGoalCategory().getGoalCategoryId(),
                ideaBoard.getIdea(),
                ideaBoard.getImageUrl(),
                ideaBoard.getIdeaStatus(),
                ideaBoard.getCreatedAt(),
                ideaBoard.getUpdatedAt()
        );
    }
}