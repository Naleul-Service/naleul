package com.naleul.naleul.domain.userColor.service;

import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.userColor.entity.UserColor;
import com.naleul.naleul.domain.userColor.repository.UserColorRepository;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserColorFinder {

    private final UserColorRepository userColorRepository;

    // 기존: 소유권 검증 없이 존재만 확인 (내부용/호출부에서 소유권을 이미 보장하는 경우에만 사용)
    public UserColor findUserColor(Long userColorId) {
        return userColorRepository.findById(userColorId)
                .orElseThrow(() -> new CustomException(ErrorCode.COLOR_NOT_FOUND));
    }

    // 신규: 소유권까지 함께 검증
    public UserColor findUserColor(Long userColorId, User user) {
        return userColorRepository.findByUserColorIdAndUser(userColorId, user)
                .orElseThrow(() -> new CustomException(ErrorCode.COLOR_NOT_FOUND));
    }
}