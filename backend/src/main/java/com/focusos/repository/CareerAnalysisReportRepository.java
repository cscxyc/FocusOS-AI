package com.focusos.repository;

import com.focusos.entity.CareerAnalysisReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Sprint 7-A: Career Analysis Report Repository
 */
@Repository
public interface CareerAnalysisReportRepository extends JpaRepository<CareerAnalysisReport, Long> {

    /** 查询用户的全部 Career 报告（按时间倒序） */
    List<CareerAnalysisReport> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** 通过 workflowId 查询报告（与异步 Workflow 关联） */
    Optional<CareerAnalysisReport> findByWorkflowId(String workflowId);

    /** 通过岗位名称模糊查询 */
    List<CareerAnalysisReport> findByUserIdAndJobTitleContainingOrderByCreatedAtDesc(Long userId, String jobTitle);
}
