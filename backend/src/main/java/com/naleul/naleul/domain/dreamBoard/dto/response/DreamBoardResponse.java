package com.naleul.naleul.domain.dreamBoard.dto.response;

import com.naleul.naleul.domain.dreamBoard.entity.DreamBoard;

import java.time.LocalDateTime;

public record DreamBoardResponse(
        Long dreamBoardId,
        Long goalCategoryId,   // ⭐ 추가 (선택)
        String imageUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static DreamBoardResponse from(DreamBoard dreamBoard) {
        return new DreamBoardResponse(
                dreamBoard.getDreamBoardId(),
                dreamBoard.getGoalCategory().getGoalCategoryId(),
                dreamBoard.getImageUrl(),
                dreamBoard.getCreatedAt(),
                dreamBoard.getUpdatedAt()
        );
    }
}