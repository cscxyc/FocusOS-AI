package com.focusos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.focusos.agent.AgentRegistry;
import com.focusos.agent.AgentWorkflowService;
import com.focusos.agent.FocusAgent;
import com.focusos.config.AgentWorkerConfig;
import com.focusos.entity.WorkflowInstance.Status;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * Sprint 8-E: Agent Worker Pool
 * <p>
 * 消费 {@link WorkflowScheduler} 提交的任务队列，在独立线程池中多线程执行 Agent。
 * 执行流程：入队 → 更新 RUNNING → {@link RetryManager#executeWithRetry} 包裹 Agent 调用
 * → 成功 markSuccess / 失败 markFailed。
 * <p>
 * 设计说明：
 * - 通过 {@link ApplicationContext} 懒加载 {@link AgentWorkflowService}，避免与之循环依赖
 *   （AgentWorkflowService 依赖线程池 / AgentRegistry，本类又依赖 Scheduler，直接注入会成环）。
 * - 对于在 {@link AgentRegistry} 中未注册的复合 Workflow 类型，委托给 AgentWorkflowService 处理。
 * - WorkflowScheduler 与本类互相依赖（Scheduler 提交任务给 Worker，Worker 回调 Scheduler 更新状态），
 *   通过 {@code @Lazy} 注入 Scheduler 打破构造期循环依赖，方法调用发生在运行时此时两者均已就绪。
 * <p>
 * 注意：不使用 Lombok {@code @RequiredArgsConstructor}，因为 Lombok 默认不会把字段上的
 * {@code @Lazy} 复制到构造参数上，导致循环依赖无法打破。这里显式声明构造器，在 scheduler
 * 参数上加 {@code @Lazy}。
 */
@Slf4j
@Service
public class AgentWorker {

    /** Agent Worker 线程池（Spring 管理的 ThreadPoolTaskExecutor，Bean 名 agentWorkerExecutor） */
    private final ThreadPoolTaskExecutor agentWorkerExecutor;

    /** @Lazy 打破与 WorkflowScheduler 的循环依赖 */
    private final WorkflowScheduler scheduler;
    private final ObjectMapper objectMapper;
    private final AgentRegistry agentRegistry;
    private final RetryManager retryManager;
    private final ApplicationContext applicationContext;

    /**
     * 显式构造器：scheduler 参数上的 {@code @Lazy} 确保其在使用时才解析，
     * 打破 WorkflowScheduler ↔ AgentWorker 构造期循环依赖。
     */
    public AgentWorker(
            @Qualifier(AgentWorkerConfig.AGENT_WORKER_EXECUTOR) ThreadPoolTaskExecutor agentWorkerExecutor,
            @Lazy WorkflowScheduler scheduler,
            ObjectMapper objectMapper,
            AgentRegistry agentRegistry,
            RetryManager retryManager,
            ApplicationContext applicationContext) {
        this.agentWorkerExecutor = agentWorkerExecutor;
        this.scheduler = scheduler;
        this.objectMapper = objectMapper;
        this.agentRegistry = agentRegistry;
        this.retryManager = retryManager;
        this.applicationContext = applicationContext;
    }

    /**
     * 提交任务到线程池异步执行。
     *
     * @param workflowId Workflow 唯一标识
     * @param userId     用户 ID
     * @param taskType   任务类型（对应 Agent type）
     * @param payload    输入参数（可为 null，resume/retry 场景无原始 payload）
     */
    public void enqueue(String workflowId, Long userId, String taskType, String payload) {
        agentWorkerExecutor.execute(() -> executeTask(workflowId, userId, taskType, payload));
        log.info("Workflow 已入队: workflowId={}, taskType={}, userId={}", workflowId, taskType, userId);
    }

    /**
     * 实际执行任务（在线程池工作线程中运行）：
     * 1. 更新状态为 RUNNING
     * 2. 通过 {@link RetryManager} 包裹 Agent 调用，失败自动重试
     * 3. 成功 → markSuccess；失败 → markFailed
     * <p>
     * 若 taskType 在 AgentRegistry 中无对应 Agent（复合工作流），则委托给
     * {@link AgentWorkflowService}（懒加载获取，避免循环依赖）。
     *
     * @param workflowId Workflow 唯一标识
     * @param userId     用户 ID
     * @param taskType   任务类型
     * @param payload    输入参数（可为 null）
     */
    void executeTask(String workflowId, Long userId, String taskType, String payload) {
        log.info("开始执行 Workflow: workflowId={}, taskType={}, userId={}", workflowId, taskType, userId);
        scheduler.updateProgress(workflowId, "执行中: " + taskType, 10, Status.RUNNING.name());

        try {
            Supplier<String> task = () -> {
                FocusAgent agent = agentRegistry.getAgent(taskType).orElse(null);
                if (agent == null) {
                    // 未注册的复合工作流类型，委托给 AgentWorkflowService（懒加载避免循环依赖）
                    log.info("Agent 未注册 type={}, 委托给 AgentWorkflowService 处理", taskType);
                    String goal = payload != null ? payload : workflowId;
                    getAgentWorkflowService().startWorkflowAsync(userId, goal);
                    return "delegated";
                }
                String message = payload != null ? payload : "";
                return agent.handle(message, userId, workflowId);
            };

            retryManager.executeWithRetry(task, retryManager.defaultPolicy());
            scheduler.markSuccess(workflowId);
            log.info("Workflow 执行成功: {}", workflowId);
        } catch (Exception e) {
            log.error("Workflow 执行失败: {}, error={}", workflowId, e.getMessage(), e);
            scheduler.markFailed(workflowId, e.getMessage());
        }
    }

    /**
     * 懒加载获取 {@link AgentWorkflowService}，避免与之形成构造期循环依赖。
     */
    private AgentWorkflowService getAgentWorkflowService() {
        return applicationContext.getBean(AgentWorkflowService.class);
    }
}
