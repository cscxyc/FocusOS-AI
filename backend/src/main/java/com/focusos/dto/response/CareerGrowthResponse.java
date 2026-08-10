package com.focusos.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Sprint 8-B: 职业成长规划响应 DTO
 * <p>
 * 用于 CareerGrowthAgent 输出的结构化成长规划，
 * 通过 ObjectMapper 序列化保证 JSON 稳定性（复用 Sprint 7-C-A 方案）。
 * <p>
 * 结构：
 * - currentLevel       当前能力等级定位
 * - careerGoal         职业目标
 * - skillGaps          能力 Gap 分析（技术能力 + 目标状态 + 提升原因）
 * - roadmap            三个月成长路线（分阶段）
 * - weeklyTasks        周任务计划（可执行）
 * - projects           推荐项目实践
 * - summary            总结
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CareerGrowthResponse {

    /** 当前能力等级定位（如：初级 AI 应用开发工程师） */
    @JsonProperty("currentLevel")
    private String currentLevel;

    /** 职业目标（如：具备大厂 AI 应用开发岗位竞争力的工程师） */
    @JsonProperty("careerGoal")
    private String careerGoal;

    /** 能力 Gap 分析列表 */
    @JsonProperty("skillGaps")
    @Builder.Default
    private List<SkillGap> skillGaps = new ArrayList<>();

    /** 三个月成长路线（按月分阶段） */
    @JsonProperty("roadmap")
    @Builder.Default
    private List<LearningStage> roadmap = new ArrayList<>();

    /** 周任务计划列表 */
    @JsonProperty("weeklyTasks")
    @Builder.Default
    private List<WeeklyTask> weeklyTasks = new ArrayList<>();

    /** 推荐项目实践列表 */
    @JsonProperty("projects")
    @Builder.Default
    private List<ProjectRecommendation> projects = new ArrayList<>();

    /** 总结 */
    @JsonProperty("summary")
    private String summary;

    /**
     * 能力 Gap 分析
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SkillGap {
        /** 技能名称（如：Spring Cloud） */
        @JsonProperty("skill")
        private String skill;

        /** 重要性（HIGH / MEDIUM / LOW） */
        @JsonProperty("importance")
        private String importance;

        /** 当前状态（如：未接触 / 了解概念 / 有基础实践） */
        @JsonProperty("currentStatus")
        private String currentStatus;

        /** 目标状态（如：能独立设计微服务架构） */
        @JsonProperty("targetStatus")
        private String targetStatus;

        /** 提升原因（必须对应 JD 要求） */
        @JsonProperty("reason")
        private String reason;
    }

    /**
     * 成长阶段（按月）
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LearningStage {
        /** 月份（1 / 2 / 3） */
        @JsonProperty("month")
        private Integer month;

        /** 当月目标 */
        @JsonProperty("goal")
        private String goal;

        /** 当月需掌握的技能列表 */
        @JsonProperty("skills")
        @Builder.Default
        private List<String> skills = new ArrayList<>();

        /** 当月任务列表 */
        @JsonProperty("tasks")
        @Builder.Default
        private List<String> tasks = new ArrayList<>();
    }

    /**
     * 周任务计划
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class WeeklyTask {
        /** 周次（1-12） */
        @JsonProperty("week")
        private Integer week;

        /** 任务标题 */
        @JsonProperty("title")
        private String title;

        /** 任务描述 */
        @JsonProperty("description")
        private String description;

        /** 预计耗时（小时） */
        @JsonProperty("estimatedHours")
        private Integer estimatedHours;

        /** 优先级（HIGH / MEDIUM / LOW） */
        @JsonProperty("priority")
        private String priority;
    }

    /**
     * 推荐项目实践
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ProjectRecommendation {
        /** 项目名称 */
        @JsonProperty("name")
        private String name;

        /** 项目目的 */
        @JsonProperty("purpose")
        private String purpose;

        /** 技术栈列表 */
        @JsonProperty("technologies")
        @Builder.Default
        private List<String> technologies = new ArrayList<>();

        /** 推荐理由（对应 JD 要求） */
        @JsonProperty("whyRecommended")
        private String whyRecommended;
    }
}
