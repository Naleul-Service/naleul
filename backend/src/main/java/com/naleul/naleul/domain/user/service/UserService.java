package com.naleul.naleul.domain.user.service;

import com.naleul.naleul.domain.chart.dto.response.UserPatternTypeResponse;
import com.naleul.naleul.domain.user.dto.request.UserWithdrawalRequest;
import com.naleul.naleul.domain.user.dto.response.UserNicknameUpdateResponse;
import com.naleul.naleul.domain.user.dto.response.UserResponse;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.entity.UserWithdrawal;
import com.naleul.naleul.domain.user.enums.UserPatternType;
import com.naleul.naleul.domain.user.enums.UserWithdrawalReason;
import com.naleul.naleul.domain.user.repository.UserRepository;
import com.naleul.naleul.domain.user.repository.UserWithdrawalRepository;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)  // 조회만 하는 메서드엔 readOnly 붙이는 게 실무 관례
public class UserService {

    private final UserRepository userRepository;
    private final UserFinder userFinder;
    private final UserWithdrawalRepository userWithdrawalRepository;
    private final UserDataCleaner userDataCleaner;

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(UserResponse::from)  // User 엔티티 → UserResponse DTO 변환
                .toList();
    }

    public UserResponse getUserById(Long userId) {
        User user = userFinder.findUser(userId);
        return UserResponse.from(user);
    }

    @Transactional
    public UserPatternTypeResponse updatePatternType(Long userId, UserPatternType patternType) {
        User user = userFinder.findUser(userId);
        user.updatePatternType(patternType);
        return UserPatternTypeResponse.from(user);
    }

    @Transactional
    public UserNicknameUpdateResponse updateNickname(Long userId, String nickname) {
        User user = userFinder.findUser(userId);
        user.updateUserName(nickname);
        return UserNicknameUpdateResponse.from(user);
    }

    @Transactional
    public void withdraw(Long userId, UserWithdrawalRequest request) {
        User user = userFinder.findUser(userId);
        if (request.reason() == UserWithdrawalReason.OTHER
                && (request.detail() == null || request.detail().isBlank())) {
            throw new CustomException(ErrorCode.WITHDRAWAL_DETAIL_REQUIRED);
        }

        // 1. User를 하드 삭제하기 전에 탈퇴 사유를 스냅샷으로 남긴다
        userWithdrawalRepository.save(UserWithdrawal.of(user, request.reason(), request.detail()));

        // 2. users 를 참조하는 모든 데이터를 자식 → 부모 순서로 지우고, 마지막에 User 를 삭제한다
        //    (refresh_session 도 여기서 지워져서 모든 기기가 로그아웃된다)
        //    기존에는 task/routine/카테고리 4개만 지워서 회고·색상·미션·마일스톤 등이 남아 FK 위반으로 실패했었다
        userDataCleaner.deleteAllUserData(userId);
    }
}