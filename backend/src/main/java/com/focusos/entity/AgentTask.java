package com.focusos.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Multi-Agent Workflow 任务编排实体
 * <p>
 * Sprint 6-A: 基础字段（id/goal/taskType/agentType/status/result/createdAt）
 * Sprint 6-B: 新增 startedAt/completedAt/duration 用于性能分析
 * <p>
 * 一个用户目标会被 MasterAgent 拆解为多个 AgentTask，每个任务由对应 Agent 执行。
 * 任务之间可存在依赖关系（dependsOn），实现链式调用（如 CareerAgent → LearningAgent）。
 */
@Entity
@Table(name = "agent_tasks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class AgentTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /** 任务目标描述（由 MasterAgent 规划生成） */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String goal;

    /** 任务类型：CAREER_ANALYSIS / LEARNING_PLAN / SKILL_GAP / DAILY_TASK / RESUME_OPTIMIZE 等 */
    @Column(nullable = false, length = 50)
    private String taskType;

    /** 执行该任务的 Agent 类型：career / learning / rag */
    @Column(nullable = false, length = 30)
    private String agentType;

    /**
     * 任务状态：PLANNING / RUNNING / SUCCESS / FAILED
     * Sprint 6-B 新增 Workflow 级状态在 WorkflowStatus 字段
     */
    @Column(nullable = false, length = 20)
    private String status;

    /** 依赖的前置任务 ID（可为空，表示无依赖） */
    @Column
    private Long dependsOn;

    /** 任务执行结果（Agent 输出） */
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String result;

    /** 输入参数（JSON 格式，传递给 Agent 的上下文） */
    @Column(columnDefinition = "TEXT")
    private String inputParams;

    /** 错误信息（FAILED 状态时填充） */
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    /** 工作流批次ID（同一目标拆解的任务共享同一批次ID） */
    @Column(nullable = false)
    private String workflowId;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Column
    private LocalDateTime completedAt;

    // ===== Sprint 6-B 新增字段：用于性能分析与耗时统计 =====

    /** 任务开始执行时间（RUNNING 状态时设置） */
    @Column
    private LocalDateTime startedAt;

    /** 任务耗时（毫秒，SUCCESS/FAILED 时计算） */
    @Column
    private Long durationMs;

    /** 工作流状态：STARTED / RUNNING / SUCCESS / FAILED（仅 workflow 首任务记录整体状态） */
    @Column(length = 20)
    private String workflowStatus;
}
