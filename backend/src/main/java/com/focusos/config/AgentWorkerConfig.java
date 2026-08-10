package com.focusos.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Sprint 8-E: Agent Worker 线程池配置
 * <p>
 * 为 {@link com.focusos.service.AgentWorker} 提供独立线程池，与 HTTP / Workflow 执行池隔离，
 * 避免 LLM 长时间调用相互阻塞。
 * <p>
 * 配置项（application.yml 中 focusos.agent.worker.*）：
 * - core-pool-size（默认 5）：常驻工作线程数
 * - max-pool-size（默认 20）：突发流量扩容上限
 * - queue-capacity（默认 100）：超出 core 后排队，避免无限队列导致 OOM
 * - keep-alive-seconds（默认 60）：空闲线程回收时间
 * <p>
 * 拒绝策略：CallerRunsPolicy —— 队列满时由提交线程自身执行（背压保护，不丢任务）。
 */
@Slf4j
@Configuration
public class AgentWorkerConfig {

    /** Agent Worker 线程池 Bean 名称，供 {@code @Qualifier} 注入使用 */
    public static final String AGENT_WORKER_EXECUTOR = "agentWorkerExecutor";

    @Value("${focusos.agent.worker.core-pool-size:5}")
    private int corePoolSize;

    @Value("${focusos.agent.worker.max-pool-size:20}")
    private int maxPoolSize;

    @Value("${focusos.agent.worker.queue-capacity:100}")
    private int queueCapacity;

    @Value("${focusos.agent.worker.keep-alive-seconds:60}")
    private int keepAliveSeconds;

    @Bean(name = AGENT_WORKER_EXECUTOR)
    public ThreadPoolTaskExecutor agentWorkerExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(keepAliveSeconds);
        executor.setThreadNamePrefix("agent-worker-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        log.info("AgentWorkerExecutor initialized: corePoolSize={}, maxPoolSize={}, queueCapacity={}",
                corePoolSize, maxPoolSize, queueCapacity);
        return executor;
    }
}
