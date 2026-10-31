package com.naleul.naleul.domain.habit.service;

import com.naleul.naleul.domain.goalCategory.dto.response.GoalCategoryResponse;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalCategory.reader.GoalCategoryReader;
import com.naleul.naleul.domain.goalCategory.repository.GoalCategoryRepository;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.service.UserFinder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * 기본 생활 습관 그릇(goal_mode = HABIT) 을 사용자마다 1개 보장한다 (V52).
 *
 * 왜 가입할 때가 아니라 "처음 필요할 때" 만드나
 *  - 이미 가입한 사용자도 따로 데이터 작업 없이 똑같이 갖게 된다.
 *  - 로그인(카카오 · 애플) 경로를 건드리지 않아도 된다.
 * 앱이 습관 화면(GET /habits) 이나 목표 목록(includeHabit=true)을 처음 열 때 생긴다.
 *
 * 동시에 두 요청이 와도 하나만 생기도록 DB 에 사용자당 1개 유니크 키(uk_goal_category_habit_home)가 있다.
 * 먼저 만든 쪽이 이기고, 진 쪽은 새 트랜잭션에서 실패한 뒤 이미 생긴 것을 다시 읽는다.
 */
@Slf4j
@Component
public class HabitGoalProvisioner {

    static final String NAME = "기본 생활 습관";
    static final String EMOJI = "🌱";

    private final GoalCategoryRepository goalCategoryRepository;
    private final GoalCategoryReader goalCategoryReader;
    private final UserFinder userFinder;
    private final PlannerClock clock;
    private final TransactionTemplate requiresNew;

    public HabitGoalProvisioner(GoalCategoryRepository goalCategoryRepository, GoalCategoryReader goalCategoryReader,
                                UserFinder userFinder, PlannerClock clock, PlatformTransactionManager txManager) {
        this.goalCategoryRepository = goalCategoryRepository;
        this.goalCategoryReader = goalCategoryReader;
        this.userFinder = userFinder;
        this.clock = clock;
        this.requiresNew = new TransactionTemplate(txManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 기본 생활 습관 그릇의 화면 정보 (엔티티 대신 값만 — 아래 "왜 값만 돌려주나" 참고) */
    public record HabitHome(Long goalCategoryId, String name, String emoji) {
    }

    /**
     * 있으면 그대로, 없으면 만들어서 돌려준다.
     *
     * 왜 엔티티가 아니라 값(HabitHome)만 돌려주나
     *  찾기 · 만들기를 모두 새 트랜잭션(REQUIRES_NEW) 안에서 한다. 바깥 트랜잭션은 MySQL 기본 격리 수준(REPEATABLE READ)이라
     *  처음 읽은 시점의 스냅샷만 보기 때문에, 새 트랜잭션이 막 커밋한 그릇을 바깥에서 다시 읽으면 "없음"으로 보인다.
     *  그래서 바깥에서는 이 id 로 getReferenceById 만 쓰고(FK 검사는 최신 커밋을 본다) 다시 SELECT 하지 않는다.
     */
    public HabitHome ensure(User user) {
        try {
            return requiresNew.execute(status -> {
                List<GoalCategory> existing = goalCategoryRepository.findHabitHomes(user.getUserId());
                GoalCategory home = !existing.isEmpty() ? existing.get(0)
                        : goalCategoryRepository.saveAndFlush(
                                GoalCategory.createHabitHome(user, null, NAME, EMOJI, clock.today()));
                return new HabitHome(home.getGoalCategoryId(), home.getGoalCategoryName(), home.getEmoji());
            });
        } catch (DataIntegrityViolationException race) {
            // 다른 요청이 먼저 만들었다 (uk_goal_category_habit_home) — 새 트랜잭션으로 다시 읽는다
            log.info("기본 생활 습관 그릇 동시 생성 — 이미 만든 것을 씀 (userId={})", user.getUserId());
            return requiresNew.execute(status -> {
                GoalCategory home = goalCategoryRepository.findHabitHomes(user.getUserId()).get(0);
                return new HabitHome(home.getGoalCategoryId(), home.getGoalCategoryName(), home.getEmoji());
            });
        }
    }

    /** 습관을 저장할 때 FK 로만 쓰는 참조 (SELECT 하지 않는다) */
    public GoalCategory reference(HabitHome home) {
        return goalCategoryRepository.getReferenceById(home.goalCategoryId());
    }

    /** 목표 목록(includeHabit=true) 용 — 컨트롤러에서 트랜잭션 밖에서 부른다 */
    public GoalCategoryResponse ensureResponse(Long userId) {
        HabitHome home = ensure(userFinder.findUser(userId));
        return goalCategoryReader.getResponse(home.goalCategoryId());
    }
}
