package com.focusos.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Sprint 8-C: 用户长期记忆实体
 * <p>
 * 沉淀用户技能、项目经验、学习进度、职业目标等动态成长信息。
 * 来源：
 * - MemoryAgent 自动提取（学习完成事件、项目完成事件、面试复盘）
 * - CareerGrowthAgent 反馈（用户完成了某任务/项目）
 * - 用户手动补充（POST /api/memory）
 * <p>
 * memoryType 枚举：
 * - SKILL             技能（Java / Spring Boot / RAG / Milvus）
 * - PROJECT           项目完成（FocusOS AI / Milvus优化实验）
 * - EXPERIENCE        经验沉淀（面经、踩坑记录、调优经验）
 * - GOAL              职业目标（成为大厂AI应用开发工程师）
 * - LEARNING_PROGRESS 学习进度（某课程 80% 完成）
 * - PREFERENCE        用户偏好（喜欢微服务方向、偏好React前端）
 * - ACHIEVEMENT       成就/证书（比赛获奖、证书、论文）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_memories", indexes = {
        @Index(name = "idx_memory_user", columnList = "userId"),
        @Index(name = "idx_memory_type", columnList = "memoryType"),
        @Index(name = "idx_memory_user_type_key", columnList = "userId, memoryType, memoryKey")
})
public class UserMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 记忆类型，见顶部枚举说明
     */
    @Column(name = "memory_type", length = 50)
    private String memoryType;

    /**
     * 记忆 key（技能名/项目名/目标名）。同 userId + memoryType + memoryKey 视为同一条，
     * 由 MemoryMergeStrategy 合并更新，而非新增。
     */
    @Column(name = "memory_key", length = 100)
    private String memoryKey;

    /**
     * 记忆 value（具体内容）
     * - SKILL: "完成Spring Boot企业级项目开发，掌握Nacos配置中心与Gateway网关"
     * - PROJECT: "FocusOS AI，实现多Agent协同+Milvus向量检索+SSE实时工作流推送"
     * - LEARNING_PROGRESS: "Milvus性能调优专项 80% 完成，已掌握HNSW索引优化"
     */
    @Column(name = "memory_value", columnDefinition = "TEXT")
    private String memoryValue;

    /**
     * 证据来源：
     * - MemoryAgent: "LEARNING_COMPLETED_EVENT" / "INTERVIEW_FEEDBACK"
     * - CareerGrowthAgent: "CAREER_GROWTH_TASK_COMPLETED"
     * - 手动创建: "USER_MANUAL"
     * - ResumeEvaluatorAgent: "EVALUATION_FEEDBACK"
     */
    @Column(name = "source", length = 200)
    private String source;

    /**
     * 可信度（0.0~1.0）
     * - 1.0：由用户显式确认 / 系统任务完成回调
     * - 0.7~0.9：MemoryAgent 高置信提取
     * - <0.5：低置信（仅 Agent 弱推断，可能误判）
     */
    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (confidence == null) confidence = 0.7;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
