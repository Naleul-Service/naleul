package com.naleul.naleul.domain.temporaryGoal.listener;

import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.generalCategory.repository.GeneralCategoryRepository;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus;
import com.naleul.naleul.domain.goalCategory.repository.GoalCategoryRepository;
import com.naleul.naleul.domain.goalCreation.event.GoalCreatedEvent;
import com.naleul.naleul.domain.temporaryGoal.repository.TemporaryGoalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 임시 목표에서 시작한 AI 목표가 확정되면, 임시 목표의 할 일을 새 목표로 옮기고 임시 목표를 정리한다 (BE-8).
 *
 * 왜 BEFORE_COMMIT 인가
 *  - 확정 트랜잭션 "안에서" 실행되므로, 옮기다 실패하면 목표 확정까지 함께 롤백된다
 *    (새 목표는 생겼는데 할 일은 임시 목표에 남는 어중간한 상태가 없다).
 *  - GoalConfirmService 를 고치지 않고 GoalCreatedEvent 만 듣는다.
 *  - 배치 리스너(AFTER_COMMIT)보다 먼저 실행된다.
 *
 * 옮기는 것: Task, 실제 수행 기록(TaskActual), 루틴 → 새 목표의 "기타 할 일" 세부 목표로
 * 정리: 임시 목표와 그 세부 목표(기타 할 일)를 소프트 삭제 → 사이드바 "임시" 목록에서 사라짐
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TemporaryGoalAbsorber {

    private final TemporaryGoalRepository temporaryGoalRepository;
    private final GoalCategoryRepository goalCategoryRepository;
    private final GeneralCategoryRepository generalCategoryRepository;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onGoalCreated(GoalCreatedEvent event) {
        GoalCategory source = temporaryGoalRepository.findSourceOfConfirmedGoal(event.goalCategoryId()).stream()
                .findFirst().orElse(null);
        if (source == null || !source.isTemporary() || source.getGoalCategoryStatus() == GoalCategoryStatus.DELETED) {
            return; // 일반 AI 목표 생성 (임시 목표에서 시작하지 않음)
        }
        GoalCategory target = goalCategoryRepository.findById(event.goalCategoryId())
                .orElseThrow(() -> new IllegalStateException("확정된 목표가 없음: " + event.goalCategoryId()));

        GeneralCategory bucket = generalCategoryRepository.findDefaultBuckets(target.getGoalCategoryId()).stream()
                .findFirst()
                .orElseGet(() -> generalCategoryRepository.save(GeneralCategory.createDefaultBucket(target)));

        Long fromId = source.getGoalCategoryId();
        int tasks = temporaryGoalRepository.moveTasks(fromId, target, bucket);
        int actuals = temporaryGoalRepository.moveActuals(fromId, target, bucket);
        int routines = temporaryGoalRepository.moveRoutines(fromId, target, bucket);

        generalCategoryRepository.findAllActiveByGoalCategoryId(fromId).forEach(GeneralCategory::delete);
        source.delete();

        log.info("[TemporaryGoal] 구체화 완료 userId={} from={} to={} tasks={} actuals={} routines={}",
                event.userId(), fromId, target.getGoalCategoryId(), tasks, actuals, routines);
    }
}
