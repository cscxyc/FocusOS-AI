package com.focusos.repository;

import com.focusos.entity.LLMCallLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 7-C-B: LLM 调用日志 Repository
 */
@Repository
public interface LLMCallLogRepository extends JpaRepository<LLMCallLog, Long> {

    /** 查询用户最近的 LLM 调用日志 */
    List<LLMCallLog> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);

    /** 查询指定 Workflow 的全部 LLM 调用 */
    List<LLMCallLog> findByWorkflowIdOrderByCreatedAtAsc(String workflowId);

    /**
     * Sprint 8-D: 回填评估分数到最近一条 LLM 调用日志
     * 用于 Cost / Quality Tradeoff 分析
     */
    @Modifying
    @Transactional
    @Query("UPDATE LLMCallLog l SET l.evaluationScore = :score WHERE l.id = :logId")
    int updateEvaluationScore(@Param("logId") Long logId, @Param("score") Integer score);

    /** 按 Agent 类型统计调用次数和 Token */
    @Query("SELECT l.agentType, COUNT(l), COALESCE(SUM(l.inputTokens), 0), COALESCE(SUM(l.outputTokens), 0), COALESCE(AVG(l.latencyMs), 0) " +
           "FROM LLMCallLog l WHERE l.userId = :userId " +
           "GROUP BY l.agentType ORDER BY COUNT(l) DESC")
    List<Object[]> aggregateByAgent(@Param("userId") Long userId);

    /** 统计用户总调用次数、成功/失败、总 token */
    @Query("SELECT COUNT(l), SUM(CASE WHEN l.success = true THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN l.success = false THEN 1 ELSE 0 END), " +
           "COALESCE(SUM(l.inputTokens), 0), COALESCE(SUM(l.outputTokens), 0), COALESCE(SUM(l.latencyMs), 0) " +
           "FROM LLMCallLog l WHERE l.userId = :userId")
    Object[] aggregateSummary(@Param("userId") Long userId);

    // ============================================================
    // Sprint 8-E: Token 成本追踪 (Task 9)
    // ============================================================

    /** Sprint 8-E: 按 Agent 类型聚合成本（用于 Agent 成本排行），返回 [agentType, totalCost, totalTokens, callCount] */
    @Query("SELECT l.agentType, COALESCE(SUM(l.estimatedCost), 0), " +
           "COALESCE(SUM(l.inputTokens) + SUM(l.outputTokens), 0), COUNT(l) " +
           "FROM LLMCallLog l WHERE l.userId = :userId AND l.createdAt >= :since " +
           "GROUP BY l.agentType ORDER BY COALESCE(SUM(l.estimatedCost), 0) DESC")
    List<Object[]> aggregateCostByAgent(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    /** Sprint 8-E: 用户今日 Token 消耗与成本汇总，返回 [totalTokens, totalCost, callCount] */
    @Query("SELECT COALESCE(SUM(l.inputTokens) + SUM(l.outputTokens), 0), " +
           "COALESCE(SUM(l.estimatedCost), 0), COUNT(l) " +
           "FROM LLMCallLog l WHERE l.userId = :userId AND l.createdAt >= :since")
    Object[] aggregateCostSummary(@Param("userId") Long userId, @Param("since") LocalDateTime since);
}
