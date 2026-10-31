package com.naleul.naleul.domain.user.service;

import com.naleul.naleul.domain.user.dto.request.CharacterChangeRequest;
import com.naleul.naleul.domain.user.dto.response.CharacterResponse;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.repository.UserRepository;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CharacterService {

    private final UserRepository userRepository;
    private final UserFinder userFinder;

    @Transactional(readOnly = true)
    public CharacterResponse getCharacter(Long userId) {
        User user = userFinder.findUser(userId);
        return CharacterResponse.from(user);
    }

    public CharacterResponse changeCharacter(Long userId, CharacterChangeRequest request) {
        User user = userFinder.findUser(userId);
        user.changeCharacter(request.getCharacterType());
        return CharacterResponse.from(user);
    }
}