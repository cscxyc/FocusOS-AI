package com.focusos.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Sprint 7-C-A: 简历版本实体
 * <p>
 * 用户可基于 Career Workflow 的 ResumeOptimizationAgent 输出创建简历版本，
 * 支持历史版本管理、查看、更新、删除。
 * <p>
 * 一个 ResumeVersion 关联一个 CareerAnalysisReport（sourceReportId），
 * 也可独立创建（手动编辑）。
 */
@Entity
@Table(name = "resume_versions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumeVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /** 目标岗位（如：AI应用开发工程师） */
    @Column(length = 200)
    private String targetPosition;

    /** 版本名称（如：v1.0_字节AI岗_20260806） */
    @Column(length = 200)
    private String versionName;

    /** 简历内容（Markdown 格式，包含个人摘要、经历、项目、技能等） */
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String content;

    /** 来源 CareerAnalysisReport ID（可选，独立创建时为 null） */
    @Column
    private Long sourceReportId;

    /** 是否为当前激活版本（每个用户同一岗位只能有一个激活版本） */
    @Column(nullable = false)
    private Boolean isActive;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column
    private LocalDateTime updatedAt;
}
