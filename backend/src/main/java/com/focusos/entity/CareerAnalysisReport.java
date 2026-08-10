package com.focusos.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Sprint 7-A: Career Analysis Report 持久化实体
 * <p>
 * 一次完整的 Career Workflow（JD分析→简历优化→技能差距→学习计划→面试准备）
 * 的结构化结果会保存为一条 CareerAnalysisReport 记录，支持历史查看与对比。
 */
@Entity
@Table(name = "career_analysis_reports")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class CareerAnalysisReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /** 关联的 Workflow ID（Sprint 6-B 异步 Workflow） */
    @Column
    private String workflowId;

    /** 岗位名称（从 JD 解析） */
    @Column(length = 200)
    private String jobTitle;

    /** 公司名称（从 JD 解析，可为空） */
    @Column(length = 200)
    private String company;

    /** 原始 JD 内容 */
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String jobDescription;

    /** 匹配度 0-100 */
    @Column
    private Integer matchScore;

    /** 候选人画像摘要（基于 Personal RAG 真实资料） */
    @Column(columnDefinition = "TEXT")
    private String candidateProfile;

    /** 优势（JSON 数组字符串） */
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String advantages;

    /** 不足（JSON 数组字符串） */
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String gaps;

    /** 简历优化建议（JSON，由 ResumeOptimizationAgent 生成） */
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String resumeSuggestions;

    /** 学习计划（JSON，由 LearningAgent 生成） */
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String learningPlan;

    /** 面试准备题（JSON 数组字符串） */
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String interviewQuestions;

    /** 资料是否充足（false 时提示用户补充简历/项目） */
    @Column(nullable = false)
    private Boolean profileSufficient;

    /** 整体建议（MasterAgent 综合汇总） */
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String overallRecommendation;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
