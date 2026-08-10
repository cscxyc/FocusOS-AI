package com.focusos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.focusos.entity.WorkflowInstance;
import com.focusos.entity.WorkflowInstance.Status;
import com.focusos.repository.WorkflowInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Sprint 8-E: Workflow 调度服务
 * <p>
 * 统一管理 Workflow 生命周期：提交 / 暂停 / 恢复 / 重试 / 进度更新 / 终态标记。
 * 持久化基于 {@link WorkflowInstance}，实际执行委托给 {@link AgentWorker} 异步消费。
 * <p>
 * 注意：payload 不入库（仅 workflowType 持久化），resume / retry 时以 workflowId 作为上下文重新入队，
 * 由 Agent 在执行时按需从其他存储（如 AgentTask、知识库）重建上下文。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowScheduler {

    private final WorkflowInstanceRepository repository;
    private final AgentWorker agentWorker;
    private final ObjectMapper objectMapper;

    /**
     * 提交 Workflow：创建 PENDING 实例并入队异步执行。
     *
     * @param userId       用户 ID
     * @param workflowType Workflow 类型（对应 Agent type）
     * @param payload      输入参数（透传给 AgentWorker，不入库）
     * @return workflowId
     */
    @Transactional
    public String submitWorkflow(Long userId, String workflowType, String payload) {
        String workflowId = generateWorkflowId(workflowType);

        WorkflowInstance instance = WorkflowInstance.builder()
                .workflowId(workflowId)
                .userId(userId)
                .workflowType(workflowType)
                .status(Status.PENDING.name())
                .currentTask("已提交，等待执行")
                .progress(0)
                .build();
        repository.save(instance);
        log.info("Workflow 提交: workflowId={}, userId={}, type={}", workflowId, userId, workflowType);

        agentWorker.enqueue(workflowId, userId, workflowType, payload);
        return workflowId;
    }

    /** 暂停 Workflow（运行中 → PAUSED，需手动 resume 恢复） */
    @Transactional
    public void pauseWorkflow(String workflowId) {
        WorkflowInstance instance = loadInstance(workflowId);
        instance.setStatus(Status.PAUSED.name());
        repository.save(instance);
        log.info("Workflow 已暂停: {}", workflowId);
    }

    /** 恢复已暂停的 Workflow（PAUSED → PENDING，重新入队执行） */
    @Transactional
    public void resumeWorkflow(String workflowId) {
        WorkflowInstance instance = loadInstance(workflowId);
        instance.setStatus(Status.PENDING.name());
        repository.save(instance);
        log.info("Workflow 恢复执行: {}", workflowId);

        // payload 不持久化，恢复时以 workflowId 作为上下文重新入队
        agentWorker.enqueue(workflowId, instance.getUserId(), instance.getWorkflowType(), null);
    }

    /** 重试失败的 Workflow（FAILED → RETRYING，重新入队执行） */
    @Transactional
    public void retryWorkflow(String workflowId) {
        WorkflowInstance instance = loadInstance(workflowId);
        instance.setStatus(Status.RETRYING.name());
        instance.setErrorMessage(null);
        repository.save(instance);
        log.info("Workflow 重试: {}", workflowId);

        agentWorker.enqueue(workflowId, instance.getUserId(), instance.getWorkflowType(), null);
    }

    /** 查询单个 Workflow 实例 */
    public Optional<WorkflowInstance> getWorkflow(String workflowId) {
        return repository.findByWorkflowId(workflowId);
    }

    /** 查询用户的 Workflow 历史（按创建时间倒序） */
    public List<WorkflowInstance> getUserWorkflows(Long userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * 更新执行进度（AgentWorker 在执行过程中高频调用）。
     */
    @Transactional
    public void updateProgress(String workflowId, String currentTask, int progress, String status) {
        int updated = repository.updateStatus(status, currentTask, progress, workflowId);
        if (updated == 0) {
            log.warn("updateProgress 未命中: workflowId={}, status={}, progress={}", workflowId, status, progress);
        }
    }

    /** 标记 Workflow 成功（status=SUCCESS, progress=100, completedAt=now） */
    @Transactional
    public void markSuccess(String workflowId) {
        WorkflowInstance instance = loadInstance(workflowId);
        instance.setStatus(Status.SUCCESS.name());
        instance.setProgress(100);
        instance.setCompletedAt(LocalDateTime.now());
        repository.save(instance);
        log.info("Workflow 执行成功: {}", workflowId);
    }

    /** 标记 Workflow 失败（status=FAILED, errorMessage, completedAt=now） */
    @Transactional
    public void markFailed(String workflowId, String error) {
        repository.updateError(Status.FAILED.name(), error, LocalDateTime.now(), workflowId);
        log.warn("Workflow 执行失败: {}, error={}", workflowId, error);
    }

    // ============================================================
    // 内部辅助方法
    // ============================================================

    private WorkflowInstance loadInstance(String workflowId) {
        return repository.findByWorkflowId(workflowId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow 不存在: " + workflowId));
    }

    /** 根据 workflowType 生成带前缀的 workflowId（UUID 截断 8 位） */
    private String generateWorkflowId(String workflowType) {
        String prefix = workflowType != null && !workflowType.isBlank() ? workflowType.toLowerCase() : "wf";
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
