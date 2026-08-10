package com.focusos.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Sprint 8-E: Workflow 状态持久化实体
 * <p>
 * 统一记录一条 Workflow 的完整生命周期（提交 / 运行 / 成功 / 失败 / 重试 / 暂停），
 * 配合 {@link com.focusos.service.WorkflowScheduler} 与 {@link com.focusos.service.AgentWorker}
 * 实现 Workflow 的可恢复调度与进度追踪。
 * <p>
 * 状态流转：
 * PENDING（已提交，待执行）→ RUNNING（执行中）→ SUCCESS / FAILED
 * 失败后可进入 RETRYING 重新入队；运行中可被 PAUSED，恢复后回到 PENDING。
 */
@Entity
@Table(name = "workflow_instances", indexes = {
        @Index(name = "idx_wf_user", columnList = "userId"),
        @Index(name = "idx_wf_workflow_id", columnList = "workflowId"),
        @Index(name = "idx_wf_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowInstance {

    /** Workflow 状态枚举（DB 中以 String 存储，便于跨多状态迁移与查询） */
    public enum Status {
        /** 已提交，等待 AgentWorker 消费 */
        PENDING,
        /** 执行中 */
        RUNNING,
        /** 执行成功 */
        SUCCESS,
        /** 执行失败 */
        FAILED,
        /** 重试中 */
        RETRYING,
        /** 已暂停（可恢复） */
        PAUSED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Workflow 唯一标识（UUID 截断，长度 64） */
    @Column(nullable = false, length = 64)
    private String workflowId;

    @Column(nullable = false)
    private Long userId;

    /** Workflow 类型：CAREER_ANALYSIS / LEARNING_PLAN 等（长度 50） */
    @Column(nullable = false, length = 50)
    private String workflowType;

    /** 当前状态：取值见 {@link Status}（长度 20） */
    @Column(nullable = false, length = 20)
    private String status;

    /** 当前执行的任务描述（用于前端展示进度，长度 200） */
    @Column(length = 200)
    private String currentTask;

    /** 执行进度（0-100） */
    @Column
    private Integer progress;

    /** 错误信息（FAILED 状态时填充） */
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    /** 开始执行时间（进入 RUNNING 时设置） */
    @Column
    private LocalDateTime startedAt;

    /** 完成时间（SUCCESS / FAILED 时设置） */
    @Column
    private LocalDateTime completedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
