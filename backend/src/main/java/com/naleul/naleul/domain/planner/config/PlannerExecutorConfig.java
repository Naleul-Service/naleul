package com.naleul.naleul.domain.planner.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 배치 엔진 후속 작업(패턴 변경 후 재배치, AI 목표 확정 후 배치) 전용 스레드 1개.
 *
 * 왜 1개인가: EC2 1GB(-Xmx400m). 배치는 사용자당 수십 ms 라 1개로 충분하고,
 *            목표 생성 풀(goalPlanExecutor)과 분리해 AI 호출(최대 80초)에 밀리지 않게 한다.
 * 큐가 가득 차면 버린다(DiscardPolicy) — 재배치는 다음 22시 작업이나 조회 때 다시 맞춰지므로 치명적이지 않다.
 * 프로젝트에 @EnableAsync 가 없어서 GoalDraftService 처럼 executor.execute(...) 로 직접 넘긴다.
 */
@Configuration
public class PlannerExecutorConfig {

    public static final String PLANNER_EXECUTOR = "plannerExecutor";

    @Bean(name = PLANNER_EXECUTOR)
    public ThreadPoolTaskExecutor plannerExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("planner-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
