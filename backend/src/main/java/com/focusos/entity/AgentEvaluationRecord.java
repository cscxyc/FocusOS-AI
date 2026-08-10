package com.focusos.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Sprint 8-D: Agent 评估记录实体
 * <p>
 * 记录每次 Agent 输出的质量评估结果，包括：
 * - RAG 检索质量（Context Recall / Precision / Faithfulness）
 * - Agent 输出评分（accuracy / completeness / grounding / actionability）
 * - Grounding 检测结果（是否有用户事实依据）
 * - Prompt A/B Testing 对比数据
 * <p>
 * evaluationType 枚举：
 * - CAREER_ANALYSIS       职业分析评估
 * - RESUME_GENERATION     简历生成评估
 * - INTERVIEW             面试题生成评估
 * - RAG_RETRIEVAL         RAG 检索质量评估
 * - MEMORY_EXTRACTION     记忆提取质量评估
 * - GROWTH_PLAN           成长规划评估
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "agent_evaluation_records", indexes = {
        @Index(name = "idx_eval_user", columnList = "userId"),
        @Index(name = "idx_eval_workflow", columnList = "workflowId"),
        @Index(name = "idx_eval_agent", columnList = "agentType"),
        @Index(name = "idx_eval_type", columnList = "evaluationType"),
        @Index(name = "idx_eval_created", columnList = "createdAt")
})
public class AgentEvaluationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 用户 ID（严格隔离） */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 关联的 Workflow ID（可为空，如独立评估） */
    @Column(name = "workflow_id", length = 64)
    private String workflowId;

    /** 被评估的 Agent 类型：career / career_growth / interview / resume_evaluator / rag / memory */
    @Column(name = "agent_type", nullable = false, length = 50)
    private String agentType;

    /** 评估类型，见顶部枚举说明 */
    @Column(name = "evaluation_type", nullable = false, length = 50)
    private String evaluationType;

    /** Agent 输入（截断存储，用于回溯） */
    @Lob
    @Column(name = "input_text", columnDefinition = "TEXT")
    private String input;

    /** Agent 输出（截断存储，用于回溯） */
    @Lob
    @Column(name = "output_text", columnDefinition = "TEXT")
    private String output;

    /** 综合质量评分 0-100 */
    @Column(name = "score")
    private Integer score;

    /** 结构化指标 JSON（accuracy/completeness/grounding/actionability 等） */
    @Lob
    @Column(name = "metrics_json", columnDefinition = "TEXT")
    private String metricsJson;

    /** 评估反馈 / 发现的问题列表（JSON 数组字符串） */
    @Lob
    @Column(name = "feedback", columnDefinition = "TEXT")
    private String feedback;

    /** Prompt 版本标识（A/B Testing 用），如 "v1" / "v2" */
    @Column(name = "prompt_version", length = 20)
    private String promptVersion;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
