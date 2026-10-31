package com.naleul.naleul.domain.user.dto.request;

import com.naleul.naleul.domain.user.enums.CharacterType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CharacterChangeRequest {

    @NotNull(message = "캐릭터 타입은 필수입니다.")
    private CharacterType characterType;
}