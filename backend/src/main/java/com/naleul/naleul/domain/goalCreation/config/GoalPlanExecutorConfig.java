package com.naleul.naleul.domain.goalCreation.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 초안 생성(AI 계획 호출, 최대 80초)을 돌리는 전용 스레드풀.
 *
 * 왜 전용 풀인가
 *  - @Async 기본 풀을 쓰면 다른 비동기 작업(알림, 리포트 등)과 섞여서 서로 밀린다.
 *  - EC2 메모리가 1GB(-Xmx400m)라 스레드를 많이 만들 수 없다. 스레드 1개당 스택 ~1MB에
 *    AI 응답 JSON까지 들고 있으므로, 동시에 2개만 처리하고 나머지는 큐에서 기다리게 한다.
 *  - 큐(10)까지 꽉 차면 RejectedExecutionException → 서비스가 잡아서 초안을 실패 처리하고 502로 응답.
 *    (CallerRunsPolicy를 쓰면 요청 스레드가 80초 동안 붙잡혀 다른 API까지 느려지므로 쓰지 않음)
 *
 * 배포(블루그린) 중 구 버전 종료 시 진행 중인 작업을 최대 60초 기다려 준다.
 * 그래도 못 끝난 작업은 좀비가 되고, 폴링/스케줄러가 3분 뒤 FAILED(AI_TIMEOUT)로 정리한다.
 */
@Slf4j
@Configuration
public class GoalPlanExecutorConfig {

    public static final String GOAL_PLAN_EXECUTOR = "goalPlanExecutor";

    @Bean(name = GOAL_PLAN_EXECUTOR)
    public ThreadPoolTaskExecutor goalPlanExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("goal-plan-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
