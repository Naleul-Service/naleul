package com.naleul.naleul.global.security;

import com.naleul.naleul.global.jwt.JwtProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
//@Profile("!test")  // ← 이거 추가. test 프로파일일 때는 이 클래스 로드 안 함
public class SecurityConfig {

    private final JwtProvider jwtProvider;

    // ✅ 변경 — TokenReissueService 의존성 제거.
    //    JwtAuthenticationFilter가 더 이상 필터 내부에서 직접 재발급을 수행하지 않으므로
    //    (자세한 이유는 JwtAuthenticationFilter.java 주석 참고) 이 의존성이 필요 없어졌습니다.
    public SecurityConfig(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .formLogin(login -> login.disable()) // 기본 로그인 페이지 비활성화
                .httpBasic(basic -> basic.disable()) // 기본 HTTP 인증 비활성화
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/auth/**",  // 로그인, 회원가입
                                "/api/auth/kakao/callback",
                                "/api/auth/**",
                                "/api/device-token",
                                "/actuator/health",
                                "/api/v1/iap/notifications/**",
                                "/api/v1/web-billing/toss-pay/callback",  // 토스페이 → 서버 (상태는 토스페이에 다시 확인)
                                "/api/v1/play-billing/rtdn"               // Google Play 실시간 알림 (토큰 · Play 재조회로 확인)
                        ).permitAll()
                        .anyRequest().authenticated()        // 나머지는 토큰 필요
                )
                // 필터를 직접 생성하여 등록 (SecurityConfig 설정을 확실히 태움)
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtProvider),
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // www가 붙은 도메인과 안 붙은 도메인 모두 허용하는 것이 안전합니다.
        config.setAllowedOrigins(List.of(
                "http://localhost:3000",
                "http://localhost:8080",
                "https://naleul.vercel.app",
                "https://naleul.com",
                "https://www.naleul.com"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}