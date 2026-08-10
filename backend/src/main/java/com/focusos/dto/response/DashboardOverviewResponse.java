package com.focusos.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Sprint 9-A: Dashboard 首页聚合响应 DTO
 * <p>
 * 聚合用户当前 AI 职业状态：简历评分、JD 匹配最高分、模拟面试成绩、
 * 学习成长进度、Memory 数量、最近活动列表。
 * <p>
 * 数据来源（全部通过 Repository 查询，不调用其他 Controller）：
 * <ul>
 *   <li>{@link ResumeSummary} ← ResumeVersionRepository + CareerAnalysisReportRepository</li>
 *   <li>{@link CareerSummary} ← CareerAnalysisReportRepository</li>
 *   <li>{@link InterviewSummary} ← InterviewSessionRepository</li>
 *   <li>{@link GrowthSummary} ← CareerGrowthPlanRepository</li>
 *   <li>{@link ActivityItem} ← 跨实体最近活动聚合</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DashboardOverviewResponse {

    private Long userId;
    private String username;

    /** 简历评分摘要 */
    private ResumeSummary resumeScore;

    /** JD 匹配最高分摘要 */
    private CareerSummary highestMatchScore;

    /** 模拟面试成绩摘要 */
    private InterviewSummary interviewScore;

    /** 学习成长进度摘要 */
    private GrowthSummary growthProgress;

    /** Memory 数量 */
    private Integer memoryCount;

    /** 最近活动列表（按时间倒序，最多 10 条） */
    private List<ActivityItem> recentActivities;

    /** 数据生成时间戳 */
    private LocalDateTime generatedAt;

    // ============================================================
    // 内部 DTO 类
    // ============================================================

    /**
     * 简历评分摘要。
     * <p>
     * ResumeVersion 实体本身没有 score 字段，评分通过 sourceReportId
     * 关联 CareerAnalysisReport.matchScore 获取。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ResumeSummary {
        /** 当前激活简历版本 ID */
        private Long resumeId;
        /** 简历版本名称 */
        private String versionName;
        /** 目标岗位 */
        private String targetPosition;
        /** 简历评分（来自关联的 CareerAnalysisReport.matchScore，无关联时为 null） */
        private Integer score;
        /** 用户简历总数 */
        private Integer totalVersions;
        /** 是否存在激活版本 */
        private Boolean hasActiveVersion;
    }

    /**
     * 职业匹配摘要。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CareerSummary {
        /** 最高匹配分（0-100） */
        private Integer matchScore;
        /** 对应的岗位名称 */
        private String jobTitle;
        /** 对应的公司名称 */
        private String company;
        /** 关联的 workflowId */
        private String workflowId;
        /** 报告总数 */
        private Integer totalReports;
        /** 最近一次分析时间 */
        private LocalDateTime latestAnalysisAt;
    }

    /**
     * 模拟面试成绩摘要。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InterviewSummary {
        /** 平均面试分（0-100，仅统计已完成且有 score 的会话） */
        private Integer averageScore;
        /** 最高面试分 */
        private Integer highestScore;
        /** 最近一次面试分 */
        private Integer latestScore;
        /** 面试会话总数 */
        private Integer totalSessions;
        /** 已完成面试数 */
        private Integer completedSessions;
        /** 最近面试岗位 */
        private String latestJobTitle;
    }

    /**
     * 学习成长进度摘要。
     * <p>
     * CareerGrowthPlan 实体的周数信息封装在 growthPlanJson 中，
     * 这里通过 JSON 解析提取总周数与已完成周数。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class GrowthSummary {
        /** 计划总周数（从 growthPlanJson 解析，解析失败默认 12） */
        private Integer totalWeeks;
        /** 已完成周数（基于当前日期与计划创建日期推算，上限为 totalWeeks） */
        private Integer completedWeeks;
        /** 完成进度百分比（0-100） */
        private Integer progressPercent;
        /** 目标岗位 */
        private String targetPosition;
        /** 当前能力等级 */
        private String currentLevel;
        /** 活跃计划数 */
        private Integer activePlans;
        /** 计划总数 */
        private Integer totalPlans;
    }

    /**
     * 最近活动项（跨实体聚合）。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ActivityItem {
        /** 活动类型（CAREER_ANALYSIS / RESUME_OPTIMIZATION / INTERVIEW / GROWTH_PLAN / WORKFLOW） */
        private String type;
        /** 活动标题 */
        private String title;
        /** 活动描述 */
        private String description;
        /** 关联的 workflowId（可为 null） */
        private String workflowId;
        /** 状态（SUCCESS / FAILED / RUNNING / PENDING / COMPLETED 等） */
        private String status;
        /** 活动发生时间 */
        private LocalDateTime createdAt;
    }
}
