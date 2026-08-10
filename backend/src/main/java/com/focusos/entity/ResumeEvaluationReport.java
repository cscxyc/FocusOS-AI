package com.focusos.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Sprint 8-A: 简历评估报告实体
 * <p>
 * 一次 AI 简历评估的完整结果会保存为一条 ResumeEvaluationReport 记录，
 * 支持历史查看、对比同一简历在不同 JD 下的评分变化。
 * <p>
 * 关联：
 * - resumeVersionId → ResumeVersion（被评估的简历版本）
 * - careerReportId  → CareerAnalysisReport（可选，来源 JD 报告）
 */
@Entity
@Table(name = "resume_evaluation_reports", indexes = {
        @Index(name = "idx_eval_user", columnList = "userId"),
        @Index(name = "idx_eval_version", columnList = "resumeVersionId"),
        @Index(name = "idx_eval_career_report", columnList = "careerReportId")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumeEvaluationReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /** 被评估的简历版本 ID */
    @Column(nullable = false)
    private Long resumeVersionId;

    /** 来源 CareerAnalysisReport ID（可选，独立评估时为 null） */
    @Column
    private Long careerReportId;

    /** 岗位名称 */
    @Column(length = 200)
    private String jobTitle;

    /** 公司名称 */
    @Column(length = 200)
    private String company;

    /** 综合总分 0-100 */
    @Column
    private Integer score;

    /** JD 匹配度 0-100 */
    @Column
    private Integer matchScore;

    /** ATS 关键词评分 0-100 */
    @Column
    private Integer atsScore;

    /** STAR 经历评分 0-100 */
    @Column
    private Integer starScore;

    /** 完整度评分 0-100 */
    @Column
    private Integer completenessScore;

    /** 完整评估结果 JSON（含 keywordMatches / sectionScores / suggestions 等） */
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String evaluationJson;

    /** 优势（JSON 数组字符串） */
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String strengths;

    /** 不足（JSON 数组字符串） */
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String weaknesses;

    /** 优化建议（JSON 数组字符串） */
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String suggestions;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column
    private LocalDateTime updatedAt;
}
