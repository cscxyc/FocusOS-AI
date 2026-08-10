package com.focusos.repository;

import com.focusos.entity.ResumeEvaluationReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Sprint 8-A: Resume Evaluation Report Repository
 */
@Repository
public interface ResumeEvaluationReportRepository extends JpaRepository<ResumeEvaluationReport, Long> {

    /** 查询用户全部评估报告（最新优先） */
    List<ResumeEvaluationReport> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** 查询指定简历版本的全部评估历史（最新优先） */
    List<ResumeEvaluationReport> findByResumeVersionIdOrderByCreatedAtDesc(Long resumeVersionId);

    /** 查询用户指定简历版本的评估历史 */
    List<ResumeEvaluationReport> findByUserIdAndResumeVersionIdOrderByCreatedAtDesc(Long userId, Long resumeVersionId);
}
