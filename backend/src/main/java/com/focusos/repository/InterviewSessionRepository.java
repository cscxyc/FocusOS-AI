package com.focusos.repository;

import com.focusos.entity.InterviewSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Sprint 7-B: 模拟面试会话 Repository
 */
@Repository
public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {

    /** 按用户查询所有面试会话（最新优先） */
    List<InterviewSession> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** 按 workflowId 查询（Career Workflow 集成） */
    Optional<InterviewSession> findByWorkflowId(String workflowId);

    /** 按用户和岗位标题查询 */
    List<InterviewSession> findByUserIdAndJobTitleContainingOrderByCreatedAtDesc(Long userId, String jobTitle);

    /** 按状态查询 */
    List<InterviewSession> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);
}
