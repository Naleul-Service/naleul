package com.naleul.naleul.domain.ideaBoard.dto.request;

import com.naleul.naleul.domain.ideaBoard.enums.IdeaStatus;
import lombok.Getter;

@Getter
public class IdeaBoardUpdateRequest {

    private String idea;          // 내용 수정 (선택)
    private String imageUrl;      // 사진 수정 (선택)
    private IdeaStatus ideaStatus; // 상태 변경 (선택) — PENDING or APPLIED
}