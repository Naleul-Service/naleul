package com.naleul.naleul.domain.user.service;

import com.naleul.naleul.domain.user.dto.response.ExpGainResponse;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserExpService {

    private final UserFinder userFinder;
    private static final int EXP_MISSION_TASK = 1;    // 미션 Task 하루 인증
    private static final int EXP_MISSION_SUCCESS = 100; // 미션 성공

    private final UserRepository userRepository;

    // 미션 Task 인증 완료 시 (+1 exp)
    public ExpGainResponse gainMissionTaskExp(Long userId) {
        return gainExp(userId, EXP_MISSION_TASK);
    }

    // 미션 성공 시 (+100 exp)
    public ExpGainResponse gainMissionSuccessExp(Long userId) {
        return gainExp(userId, EXP_MISSION_SUCCESS);
    }

    // 공통 경험치 적립
    private ExpGainResponse gainExp(Long userId, int exp) {
        User user = userFinder.findUser(userId);

        boolean levelUp = user.gainExperience(exp); // User 엔티티에서 레벨업 계산

        return ExpGainResponse.of(exp, user, levelUp);
    }
}