package com.naleul.naleul.domain.user.controller;

import com.naleul.naleul.domain.user.dto.request.CharacterChangeRequest;
import com.naleul.naleul.domain.user.dto.response.CharacterResponse;
import com.naleul.naleul.domain.user.service.CharacterService;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me/character")
@RequiredArgsConstructor
public class CharacterController {

    private final CharacterService characterService;

    // 내 캐릭터 정보 조회
    @GetMapping
    public ResponseEntity<ApiResponse<CharacterResponse>> getCharacter(
            @AuthenticationPrincipal Long userId
    ) {
        CharacterResponse response = characterService.getCharacter(userId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.CHARACTER_FOUND, response));
    }

    // 캐릭터 변경
    @PatchMapping
    public ResponseEntity<ApiResponse<CharacterResponse>> changeCharacter(
            @AuthenticationPrincipal Long userId,
            @RequestBody CharacterChangeRequest request
    ) {
        CharacterResponse response = characterService.changeCharacter(userId, request);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.CHARACTER_UPDATED, response));
    }
}