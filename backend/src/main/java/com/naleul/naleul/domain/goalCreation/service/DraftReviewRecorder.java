package com.naleul.naleul.domain.goalCreation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto.PlanTask;
import com.naleul.naleul.domain.goalCreation.entity.GoalDraft;
import com.naleul.naleul.domain.goalCreation.enums.*;
import com.naleul.naleul.domain.goalCreation.event.DraftConfirmedEvent;
import com.naleul.naleul.domain.goalCreation.personal.PlanFitBuilder;
import com.naleul.naleul.domain.goalCreation.repository.GoalDraftRepository;
import com.naleul.naleul.domain.goalCreation.support.DraftPlanComparer;
import com.naleul.naleul.domain.goalCreation.support.DraftPlanComparer.Diff;
import com.naleul.naleul.domain.goalCreation.support.GoalClock;
import com.naleul.naleul.domain.goalCreation.support.GoalJsonConverter;
import com.naleul.naleul.domain.goalCreation.support.PlanPromptVersion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.util.*;

/**
 * AI 초안을 확정했을 때 "AI 초안 정확도" 기록 (V68, 운영 대시보드 전용 — 사용자 화면 변화 없음).
 *
 *  - 세션 1줄: 경로(다시 생성 > 말로 고치기 > 직접 수정 > 그대로) · 초안 요청 횟수 · 칸 집계 · 루틴 시간 변화
 *  - 항목 1줄: 목표 · 루틴 · 일회성 Task · 세부 목표 · 마일스톤 (저장된 routine/task id 와 연결 — 확정 뒤 비교용)
 *  - 칸 1줄: 확정 때 비교 + 말로 고치기 직전 손으로 고친 칸
 *
 * 확정 트랜잭션이 커밋된 뒤(AFTER_COMMIT) 별도 트랜잭션으로 기록한다. 실패해도 확정에는 영향이 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DraftReviewRecorder {

    private final NamedParameterJdbcTemplate jdbc;
    private final GoalDraftRepository draftRepository;
    private final GoalJsonConverter jsonConverter;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager txManager;
    private final GoalClock clock;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onConfirmed(DraftConfirmedEvent event) {
        try {
            TransactionTemplate tx = new TransactionTemplate(txManager);
            tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            tx.executeWithoutResult(status -> record(event));
        } catch (RuntimeException e) {
            log.warn("[DraftReview] 초안 정확도 기록 실패 sessionId={} reason={}", event.sessionId(), e.getMessage());
        }
    }

    /** 세션 경로 — 가장 강한 신호 하나 */
    static DraftPath pathOf(int regen, int quickFix, List<Diff> diffs) {
        if (regen > 0) return DraftPath.REGEN;
        if (quickFix > 0) return DraftPath.QUICK_FIX;
        boolean touched = diffs.stream().anyMatch(d -> d.result() == DraftFieldResult.EDITED
                || d.result() == DraftFieldResult.DELETED || d.result() == DraftFieldResult.ADDED);
        return touched ? DraftPath.MANUAL : DraftPath.AS_IS;
    }

    /** 항목 결과: 추가 > 삭제 > 고침 > 그대로 (AUTO 는 그대로) */
    static DraftFieldResult itemResult(List<Diff> fields) {
        if (fields.stream().anyMatch(d -> d.result() == DraftFieldResult.ADDED)) return DraftFieldResult.ADDED;
        if (fields.stream().anyMatch(d -> d.result() == DraftFieldResult.DELETED)) return DraftFieldResult.DELETED;
        if (fields.stream().anyMatch(d -> d.result() == DraftFieldResult.EDITED)) return DraftFieldResult.EDITED;
        return DraftFieldResult.KEPT;
    }

    void record(DraftConfirmedEvent e) {
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM goal_draft_review WHERE goal_creation_session_id = :s",
                new MapSqlParameterSource("s", e.sessionId()), Integer.class);
        if (exists != null && exists > 0) return;

        List<GoalDraft> drafts = draftRepository.findAllBySession_GoalCreationSessionIdOrderByVersionAsc(e.sessionId());
        GoalDraft confirmed = drafts.stream().filter(d -> d.getGoalDraftId().equals(e.draftId())).findFirst().orElse(null);
        GoalPlanDto ai = confirmed != null ? jsonConverter.toPlan(confirmed.getPayload()) : null;
        GoalPlanDto fin = e.finalPlan();
        if (ai == null || fin == null || ai.goal() == null || fin.goal() == null) return;

        // 초안 요청 횟수 (예전 초안은 source 가 없어 첫 초안만 INITIAL 로 본다)
        int regen = 0, quickFix = 0, style = 0, intensity = 0, made = 0;
        Integer firstQuickFix = null;
        boolean regenAfterQuickFix = false;
        List<Diff> baseEdits = new ArrayList<>();
        for (GoalDraft d : drafts) {
            DraftSource src = d.getSource();
            if (src == DraftSource.RETRY) continue;
            made++;
            if (src == DraftSource.REGEN) {
                regen++;
                if (firstQuickFix != null && d.getVersion() > firstQuickFix) regenAfterQuickFix = true;
            } else if (src == DraftSource.QUICK_FIX) {
                quickFix++;
                if (firstQuickFix == null) firstQuickFix = d.getVersion();
                baseEdits.addAll(readDiffs(d.getBaseEdits()));
            } else if (src == DraftSource.STYLE) {
                style++;
            } else if (src == DraftSource.INTENSITY) {
                intensity++;
            }
        }

        List<Diff> diffs = DraftPlanComparer.compare(ai, fin);
        int kept = count(diffs, DraftFieldResult.KEPT), edited = count(diffs, DraftFieldResult.EDITED),
                deleted = count(diffs, DraftFieldResult.DELETED), added = count(diffs, DraftFieldResult.ADDED),
                auto = count(diffs, DraftFieldResult.AUTO);
        int baseEditCount = (int) baseEdits.stream().filter(d -> d.result() != DraftFieldResult.KEPT).count();

        String intensityValue = ai.fit() != null ? ai.fit().intensity() : null;
        String styleValue = fin.goal().planningStyle() != null ? fin.goal().planningStyle().name()
                : confirmed.getPlanningStyle() != null ? confirmed.getPlanningStyle().name() : null;

        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("session", e.sessionId()).addValue("goal", e.goalCategoryId()).addValue("user", e.userId())
                .addValue("path", pathOf(regen, quickFix, diffs).name())
                .addValue("made", made).addValue("regen", regen).addValue("quickFix", quickFix)
                .addValue("style", style).addValue("intensity", intensity)
                .addValue("regenAfterQf", regenAfterQuickFix).addValue("baseEdits", baseEditCount)
                .addValue("aiFields", kept + edited + deleted).addValue("kept", kept).addValue("edited", edited)
                .addValue("deleted", deleted).addValue("added", added).addValue("auto", auto)
                .addValue("intensityValue", intensityValue).addValue("styleValue", styleValue)
                .addValue("prompt", PlanPromptVersion.CURRENT)
                .addValue("draftWeekly", PlanFitBuilder.weeklyMinutes(routines(ai)))
                .addValue("finalWeekly", PlanFitBuilder.weeklyMinutes(routines(fin)))
                .addValue("firstDraftAt", drafts.isEmpty() || drafts.get(0).getCreatedAt() == null ? null
                        : Timestamp.valueOf(drafts.get(0).getCreatedAt()))
                .addValue("confirmedAt", Timestamp.valueOf(clock.now()));
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO goal_draft_review (goal_creation_session_id, goal_category_id, user_id, path,
                    draft_count, regen_count, quick_fix_count, style_change_count, intensity_change_count,
                    regen_after_quick_fix, base_edit_count, ai_fields, kept_fields, edited_fields, deleted_fields,
                    added_items, auto_fields, intensity, planning_style, prompt_version,
                    draft_weekly_minutes, confirmed_weekly_minutes, first_draft_at, confirmed_at)
                VALUES (:session, :goal, :user, :path, :made, :regen, :quickFix, :style, :intensity,
                    :regenAfterQf, :baseEdits, :aiFields, :kept, :edited, :deleted, :added, :auto,
                    :intensityValue, :styleValue, :prompt, :draftWeekly, :finalWeekly, :firstDraftAt, :confirmedAt)
                """, p, key, new String[]{"goal_draft_review_id"});
        long reviewId = Objects.requireNonNull(key.getKey()).longValue();

        // 항목별 → 칸별
        Map<String, Long> entityByKey = entityIds(e.goalCategoryId(), fin);
        Map<String, List<Diff>> byItem = new LinkedHashMap<>();
        for (Diff d : diffs) byItem.computeIfAbsent(d.kind() + "|" + d.key(), k -> new ArrayList<>()).add(d);
        List<MapSqlParameterSource> fieldRows = new ArrayList<>();
        for (List<Diff> fields : byItem.values()) {
            Diff first = fields.get(0);
            KeyHolder itemKey = new GeneratedKeyHolder();
            jdbc.update("""
                    INSERT INTO goal_draft_review_item (goal_draft_review_id, item_kind, item_key, label, result, entity_id)
                    VALUES (:review, :kind, :key, :label, :result, :entity)
                    """, new MapSqlParameterSource()
                    .addValue("review", reviewId).addValue("kind", first.kind().name()).addValue("key", first.key())
                    .addValue("label", first.label()).addValue("result", itemResult(fields).name())
                    .addValue("entity", first.kind() == DraftItemKind.GOAL ? e.goalCategoryId()
                            : entityByKey.get(first.kind() + "|" + first.key())),
                    itemKey, new String[]{"goal_draft_review_item_id"});
            long itemId = Objects.requireNonNull(itemKey.getKey()).longValue();
            for (Diff d : fields) fieldRows.add(fieldRow(reviewId, itemId, DraftStage.CONFIRM, d));
        }
        for (Diff d : baseEdits) fieldRows.add(fieldRow(reviewId, null, DraftStage.BEFORE_QUICK_FIX, d));
        if (!fieldRows.isEmpty()) {
            jdbc.batchUpdate("""
                    INSERT INTO goal_draft_review_field (goal_draft_review_id, goal_draft_review_item_id, stage, item_kind,
                        field, result, direction, delta, ai_value, final_value)
                    VALUES (:review, :item, :stage, :kind, :field, :result, :direction, :delta, :ai, :fin)
                    """, fieldRows.toArray(MapSqlParameterSource[]::new));
        }
        log.info("[DraftReview] 기록 sessionId={} path={} fields={} baseEdits={}",
                e.sessionId(), pathOf(regen, quickFix, diffs), diffs.size(), baseEdits.size());
    }

    /**
     * 확정 계획의 루틴 · 일회성 Task → 저장된 routine_id · task_id.
     * GoalPlanPersister 가 계획 순서대로 한 트랜잭션에서 저장하므로 id 순서 = 계획 순서. 개수가 다르면 연결하지 않는다.
     */
    private Map<String, Long> entityIds(Long goalId, GoalPlanDto fin) {
        MapSqlParameterSource p = new MapSqlParameterSource("g", goalId);
        List<Long> routineIds = jdbc.queryForList(
                "SELECT routine_id FROM routine WHERE goal_category_id = :g ORDER BY routine_id", p, Long.class);
        List<Long> taskIds = jdbc.queryForList(
                "SELECT task_id FROM task WHERE goal_category_id = :g AND routine_id IS NULL ORDER BY task_id", p, Long.class);
        List<PlanTask> tasks = fin.tasks();
        long routineCount = tasks.stream().filter(PlanTask::isRoutine).count();
        long oneTimeCount = tasks.size() - routineCount;
        Map<String, Long> out = new HashMap<>();
        int r = 0, o = 0;
        for (int i = 0; i < tasks.size(); i++) {
            PlanTask t = tasks.get(i);
            String key = t.tempId() != null && !t.tempId().isBlank()
                    ? (t.tempId().length() > 40 ? t.tempId().substring(0, 40) : t.tempId()) : "new-t-" + i;
            if (t.isRoutine()) {
                if (routineIds.size() == routineCount) out.put(DraftItemKind.ROUTINE + "|" + key, routineIds.get(r));
                r++;
            } else {
                if (taskIds.size() == oneTimeCount) out.put(DraftItemKind.ONE_TIME + "|" + key, taskIds.get(o));
                o++;
            }
        }
        return out;
    }

    private static MapSqlParameterSource fieldRow(long reviewId, Long itemId, DraftStage stage, Diff d) {
        return new MapSqlParameterSource()
                .addValue("review", reviewId).addValue("item", itemId).addValue("stage", stage.name())
                .addValue("kind", d.kind().name()).addValue("field", d.field().name()).addValue("result", d.result().name())
                .addValue("direction", d.direction() != null ? d.direction().name() : null).addValue("delta", d.delta())
                .addValue("ai", d.aiValue()).addValue("fin", d.finalValue());
    }

    private List<Diff> readDiffs(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Diff>>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private static List<PlanTask> routines(GoalPlanDto plan) {
        return plan.tasks().stream().filter(PlanTask::isRoutine).toList();
    }

    private static int count(List<Diff> diffs, DraftFieldResult r) {
        return (int) diffs.stream().filter(d -> d.result() == r).count();
    }
}
