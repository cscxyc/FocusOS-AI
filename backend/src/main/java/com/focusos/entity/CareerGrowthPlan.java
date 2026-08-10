package com.focusos.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Sprint 8-B: 职业成长规划实体
 * <p>
 * 一次 CareerGrowthAgent 生成的完整成长规划会保存为一条 CareerGrowthPlan 记录，
 * 支持历史查看、对比不同阶段的规划变化。
 * <p>
 * 关联：
 * - resumeVersionId → ResumeVersion（被评估的简历版本）
 * - evaluationId    → ResumeEvaluationReport（来源评分报告，提供评分上下文）
 */
@Entity
@Table(name = "career_growth_plans", indexes = {
        @Index(name = "idx_growth_user", columnList = "userId"),
        @Index(name = "idx_growth_resume", columnList = "resumeVersionId"),
        @Index(name = "idx_growth_evaluation", columnList = "evaluationId")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CareerGrowthPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /** 被评估的简历版本 ID */
    @Column
    private Long resumeVersionId;

    /** 来源 ResumeEvaluationReport ID */
    @Column
    private Long evaluationId;

    /** 目标岗位 */
    @Column(length = 200)
    private String targetPosition;

    /** 公司名称 */
    @Column(length = 200)
    private String company;

    /** 当前能力等级定位 */
    @Column(length = 100)
    private String currentLevel;

    /** 完整成长规划 JSON（含 skillGaps / roadmap / weeklyTasks / projects 等） */
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String growthPlanJson;

    /** 规划状态（ACTIVE / ARCHIVED） */
    @Column(length = 50)
    private String status;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column
    private LocalDateTime updatedAt;
}
