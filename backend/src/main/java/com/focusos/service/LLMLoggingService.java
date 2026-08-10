package com.focusos.service;

import com.focusos.agent.LLMCallContext;
import com.focusos.entity.LLMCallLog;
import com.focusos.repository.LLMCallLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Sprint 7-C-B: LLM 调用日志服务
 * <p>
 * 职责：
 * 1. recordCall — 由 LoggingChatLanguageModel 装饰器调用，记录每次 LLM 调用
 * 2. getSummary — 聚合统计（总调用数、成功/失败、总 Token、平均延迟、按 Agent 分组）
 * 3. getRecentLogs — 查询用户最近调用记录
 * 4. getWorkflowLogs — 查询指定 Workflow 的全部调用
 * <p>
 * 注意：recordCall 不使用 @Async，因为 @Async 会切换到新线程导致
 * LLMCallContext (ThreadLocal) 丢失。DB insert 延迟 <5ms，远小于 LLM 调用耗时（秒级），
 * 同步写入不会成为瓶颈。使用 REQUIRES_NEW 传播，独立事务，避免影响调用方事务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LLMLoggingService {

    private final LLMCallLogRepository logRepository;

    /** Token 估算系数：混合中英文约 2.5 字符/token */
    private static final double CHARS_PER_TOKEN = 2.5;

    /** Sprint 8-E: 每 1000 输入 Token 的估算成本（美元） */
    @Value("${focusos.quota.cost-per-1k-input-tokens:0.004}")
    private double costPer1kInput;

    /** Sprint 8-E: 每 1000 输出 Token 的估算成本（美元） */
    @Value("${focusos.quota.cost-per-1k-output-tokens:0.012}")
    private double costPer1kOutput;

    /**
     * 记录一次 LLM 调用（由 LoggingChatLanguageModel 自动调用）
     * <p>
     * 从 ThreadLocal (LLMCallContext) 读取 userId / workflowId / agentType
     * 同步执行以确保 ThreadLocal 上下文可用，使用 REQUIRES_NEW 独立事务
     * <p>
     * Sprint 8-E: 同步计算 estimatedCost 并写入 LLMCallLog，供 Dashboard 成本统计使用
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordCall(String model, String inputText, String outputText,
                            long latencyMs, boolean success, String errorMessage) {
        try {
            LLMCallContext.Context ctx = LLMCallContext.get();

            LLMCallLog logEntry = new LLMCallLog();
            // 上下文可能未设置（如 AgentRouter 独立调用），使用默认值
            logEntry.setUserId(ctx != null && ctx.userId() != null ? ctx.userId() : 0L);
            logEntry.setWorkflowId(ctx != null ? ctx.workflowId() : null);
            logEntry.setAgentType(ctx != null && ctx.agentType() != null ? ctx.agentType() : "unknown");
            logEntry.setModel(model != null ? model : "unknown");
            int inputTokens = estimateTokens(inputText);
            int outputTokens = success ? estimateTokens(outputText) : 0;
            logEntry.setInputTokens(inputTokens);
            logEntry.setOutputTokens(outputTokens);
            logEntry.setLatencyMs(latencyMs);
            logEntry.setSuccess(success);
            logEntry.setErrorMessage(errorMessage);
            logEntry.setEstimatedCost(calculateCost(inputTokens, outputTokens));

            logRepository.save(logEntry);
        } catch (Exception e) {
            // 日志记录失败不应影响主流程
            log.warn("Failed to record LLM call log: {}", e.getMessage());
        }
    }

    /** Sprint 8-E: 估算调用成本（美元） */
    private double calculateCost(int inputTokens, int outputTokens) {
        return (inputTokens / 1000.0) * costPer1kInput + (outputTokens / 1000.0) * costPer1kOutput;
    }

    /** Token 估算 */
    private Integer estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return Math.max(1, (int) (text.length() / CHARS_PER_TOKEN));
    }

    /**
     * 获取用户的 LLM 调用统计摘要
     */
    public Map<String, Object> getSummary(Long userId) {
        Object[] summary = logRepository.aggregateSummary(userId);
        List<Object[]> byAgent = logRepository.aggregateByAgent(userId);

        // Sprint 7-C-B: Spring Data JPA 可能将单行聚合结果包装为嵌套数组 [[row]]
        // 需要解包为扁平 Object[] { COUNT, SUM1, SUM2, ... }
        if (summary != null && summary.length == 1 && summary[0] instanceof Object[]) {
            summary = (Object[]) summary[0];
        }

        long totalCalls = toLong(summary, 0);
        long successCalls = toLong(summary, 1);
        long failedCalls = toLong(summary, 2);
        long totalInputTokens = toLong(summary, 3);
        long totalOutputTokens = toLong(summary, 4);
        long totalLatencyMs = toLong(summary, 5);

        List<Map<String, Object>> agentStats = new ArrayList<>();
        for (Object[] row : byAgent) {
            agentStats.add(Map.of(
                    "agentType", row[0] != null ? row[0].toString() : "unknown",
                    "callCount", toLong(row, 1),
                    "totalTokens", toLong(row, 2) + toLong(row, 3),
                    "avgLatencyMs", toLong(row, 4)
            ));
        }

        return Map.of(
                "totalCalls", totalCalls,
                "successCalls", successCalls,
                "failedCalls", failedCalls,
                "totalInputTokens", totalInputTokens,
                "totalOutputTokens", totalOutputTokens,
                "totalLatencyMs", totalLatencyMs,
                "byAgent", agentStats
        );
    }

    /**
     * 获取用户最近的 LLM 调用日志
     */
    public List<LLMCallLog> getRecentLogs(Long userId) {
        return logRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * 获取指定 Workflow 的全部 LLM 调用
     */
    public List<LLMCallLog> getWorkflowLogs(String workflowId) {
        return logRepository.findByWorkflowIdOrderByCreatedAtAsc(workflowId);
    }

    private long toLong(Object[] arr, int idx) {
        if (arr == null || idx >= arr.length || arr[idx] == null) return 0L;
        if (arr[idx] instanceof Number n) return n.longValue();
        try { return Long.parseLong(arr[idx].toString()); } catch (Exception e) { return 0L; }
    }
}
