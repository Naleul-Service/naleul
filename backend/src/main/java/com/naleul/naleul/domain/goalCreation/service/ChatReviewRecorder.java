package com.naleul.naleul.domain.goalCreation.service;

import com.naleul.naleul.domain.goalCreation.dto.response.GoalSummaryResponse;
import com.naleul.naleul.domain.goalCreation.dto.slot.GoalSlots;
import com.naleul.naleul.domain.goalCreation.dto.slot.PracticePreferenceSlot;
import com.naleul.naleul.domain.goalCreation.entity.GoalCreationMessage;
import com.naleul.naleul.domain.goalCreation.entity.GoalCreationSession;
import com.naleul.naleul.domain.goalCreation.entity.GoalReviewField;
import com.naleul.naleul.domain.goalCreation.enums.GoalCreationSessionStatus;
import com.naleul.naleul.domain.goalCreation.enums.MessageRole;
import com.naleul.naleul.domain.goalCreation.enums.ReviewField;
import com.naleul.naleul.domain.goalCreation.repository.GoalCreationMessageRepository;
import com.naleul.naleul.domain.goalCreation.repository.GoalReviewFieldRepository;
import com.naleul.naleul.domain.goalCreation.support.ChatReviewCalculator;
import com.naleul.naleul.domain.goalCreation.support.GoalClock;
import com.naleul.naleul.domain.goalCreation.support.GoalJsonConverter;
import com.naleul.naleul.domain.goalCreation.support.GoalSummaryBuilder;
import com.naleul.naleul.domain.user.enums.PlanningStyle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Objects;

/**
 * 첫 "계획 만들기" 때 정리 화면 항목별 결과를 남긴다 (V67, 운영 대시보드 "AI 채팅 정리 정확도").
 * 사용자 화면에는 아무 영향이 없고, 실패해도 계획 만들기는 그대로 진행한다.
 * 초안을 본 뒤 다시 만들기(DRAFT_READY)는 세지 않는다 — 정리 화면에서 처음 바꾼 것만.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatReviewRecorder {

    private final GoalReviewFieldRepository fieldRepository;
    private final GoalCreationMessageRepository messageRepository;
    private final GoalJsonConverter jsonConverter;
    private final GoalSummaryBuilder summaryBuilder;
    private final GoalClock clock;
    private final PlatformTransactionManager txManager;

    /**
     * @param now         계획 만들기를 누른 순간의 정리값
     * @param chosenStyle 사용자가 직접 고른 성향 (안 골랐으면 null)
     */
    public void recordFirstReview(GoalCreationSession session, GoalSlots now, PlanningStyle chosenStyle) {
        try {
            if (session.getReviewedAt() != null || session.getAiSlots() == null
                    || session.getStatus() != GoalCreationSessionStatus.READY_TO_GENERATE) return;
            Long sessionId = session.getGoalCreationSessionId();
            GoalSlots ai = jsonConverter.toSlots(session.getAiSlots());
            PlanningStyle aiStyle = session.getAiPlanningStyle();
            List<ChatReviewCalculator.Row> rows = ChatReviewCalculator.compare(ai, now, aiStyle, chosenStyle);
            GoalSummaryResponse aiText = summaryBuilder.build(ai, aiStyle, clock.today());
            GoalSummaryResponse nowText = summaryBuilder.build(now, chosenStyle != null ? chosenStyle : aiStyle, clock.today());
            Long userId = session.getUser().getUserId();

            // 별도 트랜잭션 — 여기서 DB 오류가 나도 계획 만들기 트랜잭션은 롤백되지 않게
            TransactionTemplate tx = new TransactionTemplate(txManager);
            tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            Boolean saved = tx.execute(status -> {
                // 이미 기록됨 (앞선 요청의 바깥 트랜잭션이 롤백돼 reviewed_at 만 빠진 경우) → 다시 표시만
                if (fieldRepository.existsBySessionId(sessionId)) return true;
                List<GoalCreationMessage> messages =
                        messageRepository.findAllBySession_GoalCreationSessionIdOrderByGoalCreationMessageIdAsc(sessionId);
                for (ChatReviewCalculator.Row r : rows) {
                    GoalCreationMessage question = lastQuestion(messages, r.field());
                    GoalCreationMessage answer = answerTo(messages, question, r.field());
                    fieldRepository.save(GoalReviewField.of(sessionId, userId, r.field(), r.aiFilled(), r.result(),
                            answer != null ? answer.getAnswerSource() : null,
                            question != null ? question.getGoalCreationMessageId() : null,
                            answer != null ? answer.getGoalCreationMessageId() : null,
                            text(r.field(), aiText, ai), text(r.field(), nowText, now)));
                }
                return true;
            });
            if (Boolean.TRUE.equals(saved)) session.markReviewed(clock.now());
        } catch (RuntimeException e) {
            log.warn("[ChatReview] 정리 화면 결과 기록 실패 sessionId={} reason={}",
                    session.getGoalCreationSessionId(), e.getMessage());
        }
    }

    /** 이 항목을 마지막으로 물은 AI 질문 */
    private static GoalCreationMessage lastQuestion(List<GoalCreationMessage> messages, ReviewField field) {
        GoalCreationMessage found = null;
        for (GoalCreationMessage m : messages) {
            if (m.getRole() == MessageRole.ASSISTANT && field.askedSlot().key().equals(m.getTargetSlot())) found = m;
        }
        return found;
    }

    /**
     * 그 질문에 대한 사용자 답 = 다음 AI 질문 전의 마지막 사용자 메시지
     * (AI 호출이 실패해 다른 글로 다시 보냈으면 나중 것이 실제로 반영된 답). 목표는 시작 화면에서 쓴 첫 문장일 수 있다
     */
    private static GoalCreationMessage answerTo(List<GoalCreationMessage> messages, GoalCreationMessage question,
                                                ReviewField field) {
        if (question == null) {
            if (field != ReviewField.GOAL_STATEMENT) return null;
            return messages.stream()
                    .filter(m -> m.getRole() == MessageRole.USER
                            && field.askedSlot().key().equals(m.getAnsweredSlot()))
                    .findFirst().orElse(null);
        }
        boolean after = false;
        GoalCreationMessage answer = null;
        for (GoalCreationMessage m : messages) {
            if (Objects.equals(m.getGoalCreationMessageId(), question.getGoalCreationMessageId())) {
                after = true;
                continue;
            }
            if (!after) continue;
            if (m.getRole() == MessageRole.ASSISTANT) break;
            if (m.getRole() == MessageRole.USER) answer = m;
        }
        return answer;
    }

    /** 대시보드 "항목 상세"에 보여줄 값 (정리 화면 표기와 같게) */
    private static String text(ReviewField field, GoalSummaryResponse s, GoalSlots slots) {
        return switch (field) {
            case GOAL_STATEMENT -> s.goalStatement();
            case METRIC -> s.metricText();
            case DEADLINE -> s.deadlineText();
            case APPROACH -> s.approach();
            case MOTIVATION -> s.motivation();
            case PRACTICE_PREFERENCE -> preference(s.preferenceText(), slots);
            case PLANNING_STYLE -> s.planningStyle() == PlanningStyle.SPONTANEOUS ? "몰아서 하는 즉흥형" : "차근차근 계획형";
        };
    }

    /** 요약 글에 안 보이는 "안 되는 시간 · 일하는 방식"도 붙여 어떤 걸 고쳤는지 보이게 */
    private static String preference(String summary, GoalSlots slots) {
        if (summary == null || !slots.practicePreference().isFilled()) return summary;
        PracticePreferenceSlot p = slots.practicePreference().value();
        StringBuilder sb = new StringBuilder(summary);
        if (p.unavailableNote() != null && !p.unavailableNote().isBlank()) sb.append(" · 안 되는 시간: ").append(p.unavailableNote().strip());
        if (p.workStyleAnswer() != null && !p.workStyleAnswer().isBlank() && !summary.contains(p.workStyleAnswer())) {
            sb.append(" · 방식: ").append(p.workStyleAnswer().strip());
        }
        return sb.toString();
    }
}
