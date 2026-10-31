package com.naleul.naleul.domain.goalCreation.scheduler;

import com.naleul.naleul.domain.goalCreation.support.GoalClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * AI 목표 대화의 답 원문 · 초안 요청 원문 보관 기간 6개월 (V67 · V68, 2026-10-25).
 * 6개월이 지나면 사용자가 쓴 답 원문 · 그 답에서 AI 가 뽑은 값 · 정리 화면 항목 값(글자)을 지운다.
 * 칩 · 답 방식 · 항목 결과(그대로/고침/직접 채움) 같은 집계용 값은 남겨서 대시보드 숫자는 그대로 나온다.
 * 새벽 4시 23분(KST) — 다른 스케줄러와 겹치지 않게. 한 번에 5,000줄씩 나눠 지워 잠금을 짧게.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatAnswerRetentionScheduler {

    static final int RETENTION_MONTHS = 6;
    private static final int BATCH = 5000;

    private final NamedParameterJdbcTemplate jdbc;
    private final GoalClock clock;

    @Scheduled(cron = "0 23 4 * * *", zone = "Asia/Seoul")
    public void purgeOldAnswers() {
        LocalDateTime cut = clock.now().minusMonths(RETENTION_MONTHS);
        MapSqlParameterSource p = new MapSqlParameterSource("cut", Timestamp.valueOf(cut)).addValue("batch", BATCH);
        int messages = repeat("""
                UPDATE goal_creation_message SET content = NULL, extracted = NULL
                WHERE role = 'USER' AND created_at < :cut AND (content IS NOT NULL OR extracted IS NOT NULL)
                LIMIT :batch
                """, p);
        int fields = repeat("""
                UPDATE goal_review_field SET ai_value = NULL, final_value = NULL
                WHERE created_at < :cut AND (ai_value IS NOT NULL OR final_value IS NOT NULL)
                LIMIT :batch
                """, p);
        // V68 AI 초안 정확도: 말로 고치기 · 다시 생성 요청 원문, 칸 값 · 항목 이름 (집계용 결과 · 방향은 남김)
        int drafts = repeat("""
                UPDATE goal_draft SET user_feedback = NULL, base_edits = NULL
                WHERE created_at < :cut AND (user_feedback IS NOT NULL OR base_edits IS NOT NULL)
                LIMIT :batch
                """, p);
        int draftFields = repeat("""
                UPDATE goal_draft_review_field SET ai_value = NULL, final_value = NULL
                WHERE created_at < :cut AND (ai_value IS NOT NULL OR final_value IS NOT NULL)
                LIMIT :batch
                """, p);
        int draftItems = repeat("""
                UPDATE goal_draft_review_item i JOIN goal_draft_review r ON r.goal_draft_review_id = i.goal_draft_review_id
                SET i.label = NULL
                WHERE r.created_at < :cut AND i.label IS NOT NULL
                """, p, false);
        if (messages + fields + drafts + draftFields + draftItems > 0) {
            log.info("[ChatRetention] 6개월 지난 원문 정리 messages={} reviewFields={} drafts={} draftFields={} draftItems={}",
                    messages, fields, drafts, draftFields, draftItems);
        }
    }

    /** 여러 테이블 UPDATE 는 MySQL 에서 LIMIT 를 못 써서 한 번만 실행 */
    private int repeat(String sql, MapSqlParameterSource p, boolean batched) {
        return batched ? repeat(sql, p) : jdbc.update(sql, p);
    }

    private int repeat(String sql, MapSqlParameterSource p) {
        int total = 0;
        for (int i = 0; i < 100; i++) {   // 최대 50만 줄 / 하루
            int n = jdbc.update(sql, p);
            total += n;
            if (n < BATCH) break;
        }
        return total;
    }
}
