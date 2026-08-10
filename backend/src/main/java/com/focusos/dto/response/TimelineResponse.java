package com.focusos.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Sprint 9-A: Career Journey Timeline 响应 DTO
 * <p>
 * 将用户求职流程可视化为有序的阶段列表，每个阶段对应 Career Workflow 的一个步骤。
 * <p>
 * 数据来源：
 * <ul>
 *   <li>WorkflowInstance（工作流执行状态）</li>
 *   <li>CareerAnalysisReport（JD 分析结果）</li>
 *   <li>ResumeVersion（简历优化结果）</li>
 *   <li>InterviewSession（模拟面试结果）</li>
 *   <li>CareerGrowthPlan（学习计划结果）</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TimelineResponse {

    /** 时间线关联的 workflowId（可为 null，表示聚合多个 workflow） */
    private String workflowId;

    /** 时间线阶段列表（按执行顺序） */
    private List<TimelineStage> stages;

    /** 时间线生成时间 */
    private LocalDateTime generatedAt;

    /**
     * 时间线单个阶段。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TimelineStage {
        /** 阶段标识：CAREER_ANALYSIS / RESUME_OPTIMIZATION / SKILL_GAP_ANALYSIS / LEARNING_PLAN / INTERVIEW_PREPARATION / MOCK_INTERVIEW */
        private String stage;

        /** 阶段状态：PENDING / RUNNING / SUCCESS / FAILED / SKIPPED */
        private String status;

        /** 阶段标题（中文展示） */
        private String title;

        /** 阶段描述（含结果摘要，如"AI应用开发工程师 · matchScore: 78"） */
        private String description;

        /** 关联的 workflowId（可为 null） */
        private String workflowId;

        /** 关联的实体 ID（如 reportId / resumeId / sessionId / planId） */
        private Long entityId;

        /** 阶段开始时间 */
        private LocalDateTime createdAt;

        /** 阶段完成时间 */
        private LocalDateTime completedAt;

        /** 阶段耗时（毫秒） */
        private Long durationMs;
    }
}
