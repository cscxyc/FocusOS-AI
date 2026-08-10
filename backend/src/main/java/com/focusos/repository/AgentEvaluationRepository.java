package com.focusos.repository;

import com.focusos.entity.AgentEvaluationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Sprint 8-D Task1: Agent 评估记录 Repository
 * <p>
 * 用户隔离铁律：所有查询方法必须携带 userId 参数。禁止跨 userId 读取/删除。
 */
@Repository
public interface AgentEvaluationRepository extends JpaRepository<AgentEvaluationRecord, Long> {

    // ============================================================
    // 用户级查询（全部带 userId，严格隔离）
    // ============================================================

    @Query("SELECT e FROM AgentEvaluationRecord e WHERE e.userId = :userId ORDER BY e.createdAt DESC")
    List<AgentEvaluationRecord> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);

    @Query("SELECT e FROM AgentEvaluationRecord e WHERE e.userId = :userId AND e.agentType = :agentType ORDER BY e.createdAt DESC")
    List<AgentEvaluationRecord> findByUserIdAndAgentType(@Param("userId") Long userId, @Param("agentType") String agentType);

    @Query("SELECT e FROM AgentEvaluationRecord e WHERE e.userId = :userId AND e.evaluationType = :evaluationType ORDER BY e.createdAt DESC")
    List<AgentEvaluationRecord> findByUserIdAndEvaluationType(@Param("userId") Long userId, @Param("evaluationType") String evaluationType);

    @Query("SELECT e FROM AgentEvaluationRecord e WHERE e.userId = :userId AND e.workflowId = :workflowId ORDER BY e.createdAt DESC")
    List<AgentEvaluationRecord> findByUserIdAndWorkflowId(@Param("userId") Long userId, @Param("workflowId") String workflowId);

    Optional<AgentEvaluationRecord> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    // ============================================================
    // Dashboard 统计查询
    // ============================================================

    /** 各 Agent 平均得分排行 */
    @Query("SELECT e.agentType, AVG(e.score), COUNT(e) FROM AgentEvaluationRecord e " +
            "WHERE e.userId = :userId AND e.score IS NOT NULL GROUP BY e.agentType ORDER BY AVG(e.score) DESC")
    List<Object[]> findAgentScoreRanking(@Param("userId") Long userId);

    /** 最近 N 条评估记录（趋势图用） */
    @Query("SELECT e FROM AgentEvaluationRecord e WHERE e.userId = :userId AND e.score IS NOT NULL ORDER BY e.createdAt DESC")
    List<AgentEvaluationRecord> findRecentScored(@Param("userId") Long userId);

    /** 按 agentType + promptVersion 聚合（A/B Testing 对比） */
    @Query("SELECT e.promptVersion, AVG(e.score), COUNT(e) FROM AgentEvaluationRecord e " +
            "WHERE e.userId = :userId AND e.agentType = :agentType AND e.score IS NOT NULL AND e.promptVersion IS NOT NULL " +
            "GROUP BY e.promptVersion ORDER BY AVG(e.score) DESC")
    List<Object[]> findPromptVersionComparison(@Param("userId") Long userId, @Param("agentType") String agentType);

    /** 指定 agentType 最近 limit 条（问题分析用） */
    @Query("SELECT e FROM AgentEvaluationRecord e WHERE e.userId = :userId AND e.agentType = :agentType ORDER BY e.createdAt DESC")
    List<AgentEvaluationRecord> findRecentByAgent(@Param("userId") Long userId, @Param("agentType") String agentType);

    // ============================================================
    // 用户级删除
    // ============================================================

    @Modifying
    @Transactional
    @Query("DELETE FROM AgentEvaluationRecord e WHERE e.id = :id AND e.userId = :userId")
    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Modifying
    @Transactional
    @Query("DELETE FROM AgentEvaluationRecord e WHERE e.userId = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
