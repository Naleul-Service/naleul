package com.naleul.naleul.domain.user.dto.response;

import com.naleul.naleul.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExpGainResponse {

    private int gainedExp;       // 이번에 획득한 경험치
    private int currentExp;      // 현재 경험치
    private int level;           // 현재 레벨
    private boolean levelUp;     // 레벨업 여부
    private double expProgress;  // 경험치 진행률

    public static ExpGainResponse of(int gainedExp, User user, boolean levelUp) {
        return ExpGainResponse.builder()
                .gainedExp(gainedExp)
                .currentExp(user.getExperience())
                .level(user.getLevel())
                .levelUp(levelUp)
                .expProgress(user.getExperience() / 1000.0)
                .build();
    }
}