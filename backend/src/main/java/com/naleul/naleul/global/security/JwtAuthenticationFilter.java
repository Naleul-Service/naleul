package com.naleul.naleul.global.security;

import com.naleul.naleul.global.jwt.JwtProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

// ✅ 변경 — TokenReissueService 의존성 제거.
//    기존엔 액세스 토큰이 "만료됐지만 서명은 유효"할 때, 필터가 요청 처리 도중
//    조용히 자체적으로 리프레시 토큰까지 재발급(rotate)해버렸습니다.
//    문제는 이 재발급이 iOS가 명시적으로 호출하는 /api/v1/auth/token-reissue 흐름과
//    완전히 별개로, 서로의 존재를 모른 채 같은 DB의 리프레시 토큰 레코드를 각자 갱신했다는 겁니다.
//    게다가 새로 발급된 토큰을 응답 헤더에 "Bearer " + newToken(LoginResponse 객체) 형태로
//    잘못 실어 보내고 있었고(문자열이 아니라 객체라 toString()이 그대로 찍힘),
//    클라이언트는 일반 API 응답에서 헤더를 읽지도 않으므로 이 재발급 사실 자체를 알 방법이 없었습니다.
//    결과적으로 서버 DB의 리프레시 토큰과 클라이언트가 들고 있는 리프레시 토큰이 어긋나게 되고,
//    클라이언트가 나중에 자기 쪽 흐름으로 재발급을 시도하면 "이미 회전되어 다른 값이 된 토큰"을
//    보내게 되어 실패 → 강제 로그아웃으로 이어졌습니다.
//    해결책은 재발급 경로를 하나(클라이언트가 명시적으로 부르는 /token-reissue)로 통일하는 것입니다.
//    그래서 필터는 이제 액세스 토큰이 유효하지 않으면(만료 포함) 무조건 401만 주고,
//    실제 재발급은 AuthController.reissueToken() 쪽에서만 일어나게 합니다.
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // 화이트리스트 경로 체크
        if (isWhiteList(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // 토큰 없으면 401 바로 반환
            writeUnauthorized(response, "인증이 필요합니다.");
            return; // filterChain.doFilter 호출 안 함!
        }

        String token = authHeader.replace("Bearer ", "");

        try {
            if (jwtProvider.isValid(token)) {
                // 둘러보기(로그인 전) 토큰은 읽기만: 저장 · 수정 · 삭제 · AI 호출(POST)은 로그인해야 한다
                if (jwtProvider.isGuest(token) && !isReadOnly(request.getMethod())) {
                    writeGuestLoginRequired(response);
                    return;
                }
                setAuthentication(token);
            } else {
                // ✅ 변경 — 만료든 서명 무효든 구분하지 않고 그냥 401.
                //    클라이언트(iOS)가 이 401을 받으면 자기 쪽 refreshToken으로
                //    /api/v1/auth/token-reissue를 호출해 재발급받는 흐름을 이미 갖고 있습니다.
                writeUnauthorized(response, "토큰이 만료되었거나 유효하지 않습니다.");
                return;
            }
        } catch (Exception e) {
            log.error("JWT 처리 에러: {}", e.getMessage());
            writeUnauthorized(response, "인증 처리 중 오류가 발생했습니다.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static boolean isReadOnly(String method) {
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method);
    }

    /** status 가 GUEST_LOGIN_REQUIRED 라서 클라이언트가 "로그인하고 저장하기" 안내를 띄울 수 있다 */
    private void writeGuestLoginRequired(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"message\":\"로그인하면 저장할 수 있어요.\",\"status\":\"GUEST_LOGIN_REQUIRED\",\"success\":false}"
        );
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"message\":\"" + message + "\",\"status\":\"401\",\"success\":false}"
        );
    }

    private boolean isWhiteList(String path) {
        return path.startsWith("/api/v1/auth/") ||
                path.startsWith("/api/auth/") ||
                path.startsWith("/actuator/") ||
                path.startsWith("/api/health") ||
                path.startsWith("/swagger-ui") ||
                path.startsWith("/v3/api-docs") ||
                path.equals("/swagger-ui.html");
    }

    private void setAuthentication(String token) {
        Long userId = jwtProvider.getUserId(token);
        setAuthenticationByUserId(userId);
    }

    private void setAuthenticationByUserId(Long userId) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userId, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}