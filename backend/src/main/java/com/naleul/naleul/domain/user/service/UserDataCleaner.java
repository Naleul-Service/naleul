package com.naleul.naleul.domain.user.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 회원 탈퇴 시 users 를 참조하는 모든 데이터를 "자식 → 부모" 순서로 지운다.
 *
 * 왜 네이티브 SQL 인가?
 *  - 운영 DB의 FK는 ddl-auto=update 로 만들어져서 ON DELETE CASCADE 가 없다.
 *    → users 를 참조하는 행이 하나라도 남아 있으면 DELETE FROM users 가 FK 위반으로 실패한다.
 *  - users 를 참조하는 테이블이 20개가 넘고, task → mission_participants 처럼
 *    "다른 유저의 데이터"가 이 유저의 데이터를 참조하는 경우도 있어서
 *    Repository 메서드를 하나씩 늘리는 방식은 누락이 생기기 쉽다.
 *  - JPA 의 deleteAllBy... 는 엔티티를 전부 조회 후 한 건씩 지워서 느리기도 하다.
 *
 * V33 이후 마이그레이션으로 만든 테이블 중 FK 가 ON DELETE CASCADE 인 것들은
 * (habit, life_pattern, daily_plan, goal_metric, work_topic, web_subscription 등)
 * users / goal_category 를 지울 때 DB 가 함께 지운다. 여기서는 RESTRICT(기본) FK 와 FK 없는 테이블만 직접 지운다.
 *
 * ⚠️ users 를 참조하는 새 테이블을 추가하면 반드시 아래 STATEMENTS 에도 추가할 것.
 *    (ON DELETE CASCADE 로 만들면 추가하지 않아도 된다)
 *    (scripts/sql/withdraw_user_by_email.sql 도 같은 순서를 사용한다)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserDataCleaner {

    private final EntityManager em;

    // 이 유저가 "호스트"인 미션
    private static final String HOSTED_MISSIONS =
            "SELECT m.mission_id FROM missions m WHERE m.user_id = :uid";

    // 이 유저의 참여 기록 + 이 유저가 호스트인 미션의 모든 참여 기록
    private static final String RELATED_PARTICIPANTS =
            "SELECT mp.mission_participant_id FROM mission_participants mp "
                    + "WHERE mp.user_id = :uid OR mp.mission_id IN (" + HOSTED_MISSIONS + ")";

    // 지워질 미션 인증(완료) 대상 Task: 이 유저의 Task + 사라질 참여 기록에 연결된 Task
    private static final String RELATED_TASKS =
            "SELECT t.task_id FROM task t "
                    + "WHERE t.user_id = :uid OR t.mission_participant_id IN (" + RELATED_PARTICIPANTS + ")";

    private static final String RELATED_COMPLETES =
            "SELECT c.mission_task_complete_id FROM mission_task_completes c "
                    + "WHERE c.user_id = :uid OR c.task_id IN (" + RELATED_TASKS + ")";

    private static final List<String> STATEMENTS = List.of(
            // ── 1. 미션 인증 좋아요 / 인증 ──
            "DELETE FROM mission_task_likes WHERE user_id = :uid "
                    + "OR mission_task_complete_id IN (" + RELATED_COMPLETES + ")",
            "DELETE FROM mission_task_completes WHERE user_id = :uid "
                    + "OR task_id IN (" + RELATED_TASKS + ")",

            // ── 2. 다른 유저의 Task 가 사라질 참여 기록을 가리키면 연결만 끊는다 (그 유저의 Task 는 유지) ──
            "UPDATE task SET mission_participant_id = NULL "
                    + "WHERE user_id <> :uid AND mission_participant_id IN (" + RELATED_PARTICIPANTS + ")",

            // ── 3. 미션 부가 데이터 ──
            "DELETE FROM mission_badges WHERE user_id = :uid OR mission_id IN (" + HOSTED_MISSIONS + ")",
            "DELETE FROM mission_bookmarks WHERE user_id = :uid OR mission_id IN (" + HOSTED_MISSIONS + ")",
            "DELETE FROM mission_poke WHERE sender_id = :uid OR target_user_id = :uid "
                    + "OR mission_id IN (" + HOSTED_MISSIONS + ")",

            // ── 4. 실제 수행 기록 → Task ──
            "DELETE FROM task_actual WHERE user_id = :uid "
                    + "OR task_id IN (SELECT t.task_id FROM task t WHERE t.user_id = :uid)",
            "DELETE FROM task WHERE user_id = :uid",

            // ── 5. 미션 참여 기록 → 미션 ──
            "DELETE FROM mission_participants WHERE user_id = :uid OR mission_id IN (" + HOSTED_MISSIONS + ")",
            "DELETE FROM mission_repeat_days WHERE mission_id IN (" + HOSTED_MISSIONS + ")",
            "DELETE FROM missions WHERE user_id = :uid",

            // ── 5-1. 마일스톤(task 가 참조) / AI 목표 만들기 대화 — goal_category 를 RESTRICT 로 참조 ──
            "DELETE FROM milestone WHERE goal_category_id IN "
                    + "(SELECT g.goal_category_id FROM goal_category g WHERE g.user_id = :uid)",
            "DELETE FROM goal_creation_session WHERE user_id = :uid",

            // ── 6. 루틴 ──
            "DELETE FROM routine_repeat_day WHERE routine_id IN "
                    + "(SELECT r.routine_id FROM routine r WHERE r.user_id = :uid)",
            "DELETE FROM routine WHERE user_id = :uid",

            // ── 7. 카테고리를 참조하는 기록/리포트 ──
            "DELETE FROM retrospective WHERE user_id = :uid",
            "DELETE FROM goal_completion_report WHERE user_id = :uid",
            "DELETE FROM dream_board WHERE user_id = :uid",
            "DELETE FROM idea_board WHERE user_id = :uid",
            "DELETE FROM weekly_report WHERE user_id = :uid",
            "DELETE FROM monthly_report WHERE user_id = :uid",

            // ── 8. 자서전 ──
            "DELETE FROM autobiography_answer WHERE user_id = :uid "
                    + "OR question_id IN (SELECT q.question_id FROM autobiography_question q WHERE q.user_id = :uid)",
            "DELETE FROM autobiography_question WHERE user_id = :uid",

            // ── 9. 카테고리 → 색상 ──
            "DELETE FROM general_category WHERE user_id = :uid",
            "DELETE FROM goal_category WHERE user_id = :uid",
            "DELETE FROM user_color WHERE user_id = :uid",

            // ── 10. 알림 / 기기 / 로그인 토큰 ──
            "DELETE FROM notifications WHERE user_id = :uid",
            "DELETE FROM notification_settings WHERE user_id = :uid",
            "DELETE FROM device_tokens WHERE user_id = :uid",
            "DELETE FROM `refresh-token` WHERE user_id = :uid",
            "DELETE FROM refresh_session WHERE user_id = :uid",
            "DELETE FROM ai_quota_usage WHERE user_id = :uid",

            // purchase_transaction · iap_revenue_event 는 FK 가 없고 결제 분쟁/환불 · 매출 집계용 기록이라 보존한다.

            // ── 11. 마지막으로 유저 ──
            "DELETE FROM users WHERE user_id = :uid"
    );

    /** 호출하는 쪽(UserService.withdraw)의 트랜잭션에 참여한다. 중간에 하나라도 실패하면 전부 롤백. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteAllUserData(Long userId) {
        // 영속성 컨텍스트에 쌓인 변경(탈퇴 사유 INSERT 등)을 먼저 DB 로 보낸다
        em.flush();

        for (String sql : STATEMENTS) {
            int affected = em.createNativeQuery(sql)
                    .setParameter("uid", userId)
                    .executeUpdate();
            if (affected > 0) {
                log.info("[withdraw] userId={} affected={} sql={}", userId, affected, firstWords(sql));
            }
        }

        // 네이티브 쿼리로 지운 엔티티가 1차 캐시에 남아 있지 않도록 비운다
        em.clear();
    }

    private static String firstWords(String sql) {
        return sql.length() > 60 ? sql.substring(0, 60) + "..." : sql;
    }
}
