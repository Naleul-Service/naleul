package com.naleul.naleul.domain.goalCreation.repository;

import com.naleul.naleul.domain.goalCreation.entity.GoalCreationMessage;
import com.naleul.naleul.domain.goalCreation.enums.MessageRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GoalCreationMessageRepository extends JpaRepository<GoalCreationMessage, Long> {

    List<GoalCreationMessage> findAllBySession_GoalCreationSessionIdOrderByGoalCreationMessageIdAsc(Long sessionId);

    /** 마지막 메시지. USER면 "이전 AI 호출이 실패해서 답을 못 받은 상태"라는 뜻 */
    Optional<GoalCreationMessage> findTopBySession_GoalCreationSessionIdOrderByGoalCreationMessageIdDesc(Long sessionId);

    /** 마지막 AI 질문 = 지금 사용자가 답하고 있는 슬롯을 알아내기 위함 */
    Optional<GoalCreationMessage> findTopBySession_GoalCreationSessionIdAndRoleOrderByGoalCreationMessageIdDesc(
            Long sessionId, MessageRole role);
}
