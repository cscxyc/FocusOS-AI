package com.focusos.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Sprint 8-D Task6: Prompt 版本管理实体
 * <p>
 * 支持 Prompt A/B Testing：
 * - 同一 agentType 可有多个版本（v1/v2/v3...）
 * - 同一 agentType 同时只能有一个 enabled=true 的版本
 * - EvaluationAgent 对比不同版本生成评分，择优启用
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "prompt_versions", indexes = {
        @Index(name = "idx_pv_agent", columnList = "agentType"),
        @Index(name = "idx_pv_enabled", columnList = "enabled")
})
public class PromptVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Agent 类型：career / career_growth / interview / resume_evaluator / rag / memory */
    @Column(name = "agent_type", nullable = false, length = 50)
    private String agentType;

    /** 版本号：v1 / v2 / vA / vB */
    @Column(name = "version", nullable = false, length = 20)
    private String version;

    /** Prompt 内容（YAML 格式或纯文本） */
    @Lob
    @Column(name = "prompt_content", columnDefinition = "TEXT", nullable = false)
    private String promptContent;

    /** 是否当前启用（同一 agentType 仅一个 enabled=true） */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 版本描述（如"12周学习计划版" / "STAR路线版"） */
    @Column(name = "description", length = 500)
    private String description;

    /** 该版本的平均评估得分（由 EvaluationService 更新） */
    @Column(name = "avg_score")
    private Double avgScore;

    /** 评估次数 */
    @Column(name = "eval_count")
    private Integer evalCount;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (enabled == null) enabled = false;
        if (evalCount == null) evalCount = 0;
        if (avgScore == null) avgScore = 0.0;
    }
}
