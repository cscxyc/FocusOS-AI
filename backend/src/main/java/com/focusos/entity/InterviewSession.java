package com.focusos.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Sprint 7-B: 模拟面试会话实体
 * <p>
 * 用于持久化面试题生成 + 模拟面试对话 + AI 评分，支持历史查看。
 * 一个 InterviewSession 关联一个 Career Workflow（可选），独立存在也可。
 */
@Entity
@Table(name = "interview_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InterviewSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /** 关联的 Career Workflow ID（可选，独立面试时为 null） */
    @Column
    private String workflowId;

    @Column(length = 200)
    private String jobTitle;

    @Column(length = 200)
    private String company;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String jobDescription;

    /** 生成的面试题 JSON（InterviewAgent.generateInterviewQuestions 输出） */
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String questionsJson;

    /** 模拟面试对话历史 JSON（数组：[{question, answer, evaluation}, ...]） */
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String conversationJson;

    /** 最终面试评分 0-100 */
    @Column
    private Integer score;

    /** 优势 JSON 数组 */
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String strengths;

    /** 弱点 JSON 数组 */
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String weaknesses;

    /** 改进建议 JSON 数组 */
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String improvement;

    /** 会话状态：IN_PROGRESS / COMPLETED / ABANDONED */
    @Column(nullable = false, length = 20)
    private String status;

    /** 当前已回答问题数 */
    @Column
    private Integer answeredCount;

    /** 资料是否充足（来自 Personal RAG 检索） */
    @Column(nullable = false)
    private Boolean profileSufficient;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column
    private LocalDateTime updatedAt;
}
