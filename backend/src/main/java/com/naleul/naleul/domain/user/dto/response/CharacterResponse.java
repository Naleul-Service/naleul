package com.naleul.naleul.domain.user.dto.response;

import com.naleul.naleul.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CharacterResponse {

    private Long userId;
    private String userName;
    private String characterType;
    private int level;
    private int experience;        // 현재 레벨 경험치 (0~999)
    private int totalExperience;   // 누적 경험치
    private int expToNextLevel;    // 다음 레벨까지 필요한 경험치
    private double expProgress;    // 경험치 진행률 (0.0 ~ 1.0) - 프론트 경험치 바용

    public static CharacterResponse from(User user) {
        return CharacterResponse.builder()
                .userId(user.getUserId())
                .userName(user.getUserName())
                .characterType(user.getCharacterType().name())
                .level(user.getLevel())
                .experience(user.getExperience())
                .totalExperience(user.getTotalExperience())
                .expToNextLevel(1000 - user.getExperience())
                .expProgress(user.getExperience() / 1000.0)
                .build();
    }
}