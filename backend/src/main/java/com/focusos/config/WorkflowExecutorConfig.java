package com.focusos.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Sprint 6-B: Workflow 异步执行线程池配置
 * <p>
 * 独立配置，避免 LLM 长时间调用阻塞主线程与 Tomcat HTTP 线程池。
 * <p>
 * 策略：
 * - corePoolSize=4：日常并发 Workflow 数
 * - maxPoolSize=8：突发流量扩容上限
 * - queueCapacity=32：超出 core 后排队，避免无限队列导致 OOM
 * - CallerRunsPolicy：队列满时由调用线程执行（背压保护）
 */
@Slf4j
@Configuration
@EnableAsync
public class WorkflowExecutorConfig {

    public static final String WORKFLOW_EXECUTOR = "workflowExecutor";

    @Bean(name = WORKFLOW_EXECUTOR)
    public Executor workflowExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(32);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("workflow-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        log.info("WorkflowExecutor initialized: corePoolSize=4, maxPoolSize=8, queueCapacity=32");
        return executor;
    }
}
