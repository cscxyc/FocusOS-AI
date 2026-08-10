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
 * Sprint 8-A: 简历评估响应 DTO
 * <p>
 * 用于 ResumeEvaluatorAgent 输出的结构化评估结果，
 * 通过 ObjectMapper 序列化保证 JSON 稳定性（复用 Sprint 7-C-A 方案）。
 * <p>
 * 评分体系（总分 100）：
 * - matchScore          JD 匹配度（30 分）
 * - atsScore            ATS 关键词评分（20 分）
 * - starScore           STAR 经历评分（25 分）
 * - completenessScore   完整度评分（10 分）
 * - 项目深度（15 分，包含在 starScore 中作为子项）
 * - score               综合总分（0-100）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResumeEvaluationResponse {

    /** 综合总分 0-100 */
    @JsonProperty("score")
    private Integer score;

    /** JD 匹配度 0-100（权重 30%） */
    @JsonProperty("matchScore")
    private Integer matchScore;

    /** ATS 关键词评分 0-100（权重 20%） */
    @JsonProperty("atsScore")
    private Integer atsScore;

    /** STAR 经历评分 0-100（权重 25%，含项目深度子项） */
    @JsonProperty("starScore")
    private Integer starScore;

    /** 完整度评分 0-100（权重 10%） */
    @JsonProperty("completenessScore")
    private Integer completenessScore;

    /**
     * Sprint 8-C: 成长一致性评分 0-100
     * <p>
     * 评估原则：简历中写的技能/项目，与 Personal Memory 中动态沉淀的
     * 长期成长状态（技能掌握、项目完成、学习进度）是否一致。
     * - 高分：简历写 RAG 项目 → Memory 有 Milvus 优化经验（已落地）→ 加分
     * - 低分：简历写"精通 Spring Cloud" → Memory 中无任何相关记录或 confidence<0.5 → 扣分
     * - 无 Memory：memory 中无数据时统一为 60 分（默认中性）
     */
    @JsonProperty("growthAlignmentScore")
    private Integer growthAlignmentScore;

    /** 优势列表 */
    @JsonProperty("strengths")
    @Builder.Default
    private List<String> strengths = new ArrayList<>();

    /** 不足列表 */
    @JsonProperty("weaknesses")
    @Builder.Default
    private List<String> weaknesses = new ArrayList<>();

    /** 缺失关键词列表 */
    @JsonProperty("missingKeywords")
    @Builder.Default
    private List<String> missingKeywords = new ArrayList<>();

    /** 关键词匹配明细 */
    @JsonProperty("keywordMatches")
    @Builder.Default
    private List<KeywordMatch> keywordMatches = new ArrayList<>();

    /** 各 section 评分 */
    @JsonProperty("sectionScores")
    private SectionScores sectionScores;

    /** 优化建议 */
    @JsonProperty("suggestions")
    @Builder.Default
    private List<String> suggestions = new ArrayList<>();

    /** 下一步行动 */
    @JsonProperty("recommendedActions")
    @Builder.Default
    private List<String> recommendedActions = new ArrayList<>();

    /**
     * 关键词匹配明细
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class KeywordMatch {
        /** 关键词 */
        @JsonProperty("keyword")
        private String keyword;

        /** 状态：MATCH / MISSING */
        @JsonProperty("status")
        private String status;

        /** 证据（来自简历的原文片段，MISSING 时为空） */
        @JsonProperty("evidence")
        private String evidence;
    }

    /**
     * 各 section 评分
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SectionScores {
        /** 个人摘要 section 评分 0-100 */
        @JsonProperty("summary")
        private Integer summary;

        /** 实习/工作经历 section 评分 0-100 */
        @JsonProperty("experience")
        private Integer experience;

        /** 项目经历 section 评分 0-100 */
        @JsonProperty("project")
        private Integer project;

        /** 技术栈 section 评分 0-100 */
        @JsonProperty("skills")
        private Integer skills;
    }
}
