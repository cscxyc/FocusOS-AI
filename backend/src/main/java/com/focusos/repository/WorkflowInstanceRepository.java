package com.focusos.repository;

import com.focusos.entity.WorkflowInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Sprint 8-E: WorkflowInstance 持久化 Repository
 */
@Repository
public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstance, Long> {

    /** 根据 workflowId 查询实例 */
    Optional<WorkflowInstance> findByWorkflowId(String workflowId);

    /** 查询用户的 Workflow 历史（按创建时间倒序） */
    List<WorkflowInstance> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** 按状态查询（用于调度器扫描 PENDING / RETRYING 等） */
    List<WorkflowInstance> findByStatus(String status);

    /**
     * 原子更新状态 / 当前任务 / 进度（RUNNING、进度推进时高频调用）
     *
     * @return 受影响行数
     */
    @Modifying
    @Transactional
    @Query("UPDATE WorkflowInstance w SET w.status = :status, w.currentTask = :currentTask, w.progress = :progress WHERE w.workflowId = :workflowId")
    int updateStatus(@Param("status") String status,
                     @Param("currentTask") String currentTask,
                     @Param("progress") int progress,
                     @Param("workflowId") String workflowId);

    /**
     * 原子更新为终态并写入错误信息与完成时间（FAILED / SUCCESS 时调用）
     *
     * @return 受影响行数
     */
    @Modifying
    @Transactional
    @Query("UPDATE WorkflowInstance w SET w.status = :status, w.errorMessage = :error, w.completedAt = :now WHERE w.workflowId = :workflowId")
    int updateError(@Param("status") String status,
                    @Param("error") String error,
                    @Param("now") java.time.LocalDateTime now,
                    @Param("workflowId") String workflowId);
}
