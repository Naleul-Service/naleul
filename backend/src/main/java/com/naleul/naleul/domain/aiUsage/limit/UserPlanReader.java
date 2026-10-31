package com.naleul.naleul.domain.aiUsage.limit;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/** 기록용 "지금 플랜" (FREE | PRO) — 운영 대시보드 "AI 관심도"에서 시도 당시 플랜으로 나눠 보려고 남긴다 (V70) */
@Component
@RequiredArgsConstructor
public class UserPlanReader {

    private final NamedParameterJdbcTemplate jdbc;

    /** @return "PRO" | "FREE", 사용자가 없거나 조회 실패면 null */
    public String planOf(Long userId) {
        if (userId == null) return null;
        try {
            List<String> r = jdbc.queryForList("SELECT " + UserPlanSql.planOf("u") + " FROM users u WHERE u.user_id = :id",
                    new MapSqlParameterSource("id", userId), String.class);
            return r.isEmpty() ? null : r.get(0);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
