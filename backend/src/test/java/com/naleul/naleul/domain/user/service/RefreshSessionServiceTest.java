package com.naleul.naleul.domain.user.service;

import com.naleul.naleul.domain.user.entity.RefreshSession;
import com.naleul.naleul.domain.user.repository.RefreshSessionRepository;
import com.naleul.naleul.global.exception.CustomException;
import com.naleul.naleul.global.jwt.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RefreshSessionServiceTest {

    RefreshSessionRepository repo = mock(RefreshSessionRepository.class);
    JwtProvider jwt = mock(JwtProvider.class);
    RefreshSessionService service = new RefreshSessionService(repo, jwt);

    @BeforeEach
    void setUp() {
        when(jwt.isValid(anyString())).thenReturn(true);
        when(jwt.generateRefreshToken(1L)).thenReturn("R1");
        when(repo.findByTokenHashForUpdate(anyString())).thenReturn(Optional.empty());
        when(repo.findByPreviousTokenHash(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void 현재_토큰이면_회전하고_만료를_미룬다() {
        LocalDateTime now = LocalDateTime.now();
        RefreshSession s = RefreshSession.start(1L, "R0", RefreshSessionService.hash("R0"), now, now.plusDays(1));
        when(repo.findByTokenHashForUpdate(RefreshSessionService.hash("R0"))).thenReturn(Optional.of(s));

        RefreshSessionService.Rotated r = service.rotate("R0");

        assertThat(r.refreshToken()).isEqualTo("R1");
        assertThat(s.getPreviousTokenHash()).isEqualTo(RefreshSessionService.hash("R0"));
        assertThat(s.getExpiresAt()).isAfter(now.plusDays(50));
    }

    @Test
    void 방금_회전된_직전_토큰이면_같은_새_토큰을_준다() {
        LocalDateTime now = LocalDateTime.now();
        RefreshSession s = RefreshSession.start(1L, "R0", RefreshSessionService.hash("R0"), now, now.plusDays(1));
        s.rotate("R1", RefreshSessionService.hash("R1"), LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul")), now.plusDays(60));
        when(repo.findByPreviousTokenHash(RefreshSessionService.hash("R0"))).thenReturn(Optional.of(s));

        assertThat(service.rotate("R0").refreshToken()).isEqualTo("R1");
        verify(jwt, never()).generateRefreshToken(anyLong());
    }

    @Test
    void 모르는_토큰은_401() {
        assertThatThrownBy(() -> service.rotate("X")).isInstanceOf(CustomException.class);
    }
}
