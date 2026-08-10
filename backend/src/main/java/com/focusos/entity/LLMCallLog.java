package com.focusos.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Sprint 7-C-B: LLM 调用日志实体
 * <p>
 * 记录所有 Agent 的 LLM 调用信息，用于：
 * 1. 调用链追踪（哪个 Agent 调用了几次）
 * 2. Token 成本统计（输入/输出 token）
 * 3. 延迟监控（latencyMs）
 * 4. 错误诊断（success/errorMessage）
 * 5. 前端 Dashboard 展示"本次 AI 分析的 LLM 调用次数 / Token / 耗时"
 * <p>
 * 通过 LoggingChatLanguageModel 装饰器自动记录，Agent 代码无需修改。
 */
@Entity
@Table(name = "llm_call_logs", indexes = {
        @Index(name = "idx_llm_logs_user", columnList = "userId"),
        @Index(name = "idx_llm_logs_workflow", columnList = "workflowId"),
        @Index(name = "idx_llm_logs_agent", columnList = "agentType"),
        @Index(name = "idx_llm_logs_created", columnList = "createdAt")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LLMCallLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 用户 ID（用于隔离） */
    @Column(nullable = false)
    private Long userId;

    /** 关联的 Workflow ID（可为空，如独立面试生成） */
    @Column(length = 64)
    private String workflowId;

    /** Agent 类型：career / resume_optimization / learning / interview / rag / router / master */
    @Column(nullable = false, length = 50)
    private String agentType;

    /** 模型名称：qwen-plus / text-embedding-v2 等 */
    @Column(nullable = false, length = 100)
    private String model;

    /** 输入 token 数（估算） */
    @Column
    private Integer inputTokens;

    /** 输出 token 数（估算） */
    @Column
    private Integer outputTokens;

    /** 调用耗时（毫秒） */
    @Column
    private Long latencyMs;

    /** 是否成功 */
    @Column(nullable = false)
    private Boolean success;

    /** 错误信息（失败时） */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Sprint 8-D: 评估质量评分 0-100
     * <p>
     * 由 AgentEvaluationService 在评估完成后回填。
     * 形成 Cost / Quality Tradeoff：调用成本（token + latency）+ 输出质量（evaluationScore）。
     * null 表示尚未评估。
     */
    @Column
    private Integer evaluationScore;

    /**
     * Sprint 8-E: 估算成本（美元）
     * <p>
     * 计算公式：{@code estimatedCost = inputTokens/1000 * costPer1kInput + outputTokens/1000 * costPer1kOutput}，
     * 由 {@link com.focusos.service.LLMLoggingService} 在记录日志时同步写入。
     * 用于 Dashboard 显示"今日成本"和"Agent 成本排行"。
     */
    @Column
    private Double estimatedCost;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
