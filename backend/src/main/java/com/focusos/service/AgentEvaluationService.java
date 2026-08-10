package com.focusos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.focusos.agent.EvaluationAgent;
import com.focusos.agent.EvaluationAgent.EvaluationResult;
import com.focusos.agent.GroundingChecker;
import com.focusos.agent.GroundingChecker.GroundingResult;
import com.focusos.agent.RAGEvaluator;
import com.focusos.agent.RAGEvaluator.RAGMetrics;
import com.focusos.entity.AgentEvaluationRecord;
import com.focusos.entity.PromptVersion;
import com.focusos.repository.AgentEvaluationRepository;
import com.focusos.repository.LLMCallLogRepository;
import com.focusos.repository.PromptVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Sprint 8-D: Agent 评估服务
 * <p>
 * 职责：
 * 1. 调用 EvaluationAgent 对 Agent 输出做质量评估并持久化评估记录
 * 2. 调用 GroundingChecker 做事实依据核查
 * 3. 调用 RAGEvaluator 做 RAG 流水线端到端评估
 * 4. 维护 Prompt 版本（A/B Testing）：创建 / 启用 / 评分回填
 * 5. 提供评估数据查询：排行 / 趋势 / 问题分析 / 版本对比
 * <p>
 * 用户隔离铁律：所有读写操作严格按 userId 隔离（PromptVersion 除外，全局共享）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentEvaluationService {

    private final AgentEvaluationRepository evalRepository;
    private final PromptVersionRepository promptVersionRepository;
    private final EvaluationAgent evaluationAgent;
    private final GroundingChecker groundingChecker;
    private final RAGEvaluator ragEvaluator;
    private final ObjectMapper objectMapper;
    private final LLMCallLogRepository llmLogRepository;

    // ============================================================
    // 评估 + 持久化
    // ============================================================

    /**
     * 对指定 Agent 的输出进行质量评估并持久化评估记录
     *
     * @return 已保存的评估记录
     */
    @Transactional
    public AgentEvaluationRecord evaluateAndSave(Long userId, String agentType, String evaluationType,
                                                 String input, String output, String workflowId, String promptVersion) {
        EvaluationResult result = evaluationAgent.evaluate(agentType, evaluationType, input, output, userId);

        AgentEvaluationRecord record = AgentEvaluationRecord.builder()
                .userId(userId)
                .workflowId(workflowId)
                .agentType(agentType)
                .evaluationType(evaluationType == null ? "comprehensive" : evaluationType)
                .input(input)
                .output(output)
                .score(result.getScore())
                .promptVersion(promptVersion)
                .build();

        try {
            record.setMetricsJson(objectMapper.writeValueAsString(result.getMetrics()));
        } catch (Exception e) {
            log.warn("serialize metrics failed for userId={} agentType={}", userId, agentType, e);
            record.setMetricsJson("{}");
        }

        String feedback = "";
        try {
            feedback = objectMapper.writeValueAsString(result.getIssues());
        } catch (Exception e) {
            feedback = String.valueOf(result.getIssues());
        }
        if (result.getFeedback() != null) {
            feedback = feedback + result.getFeedback();
        }
        record.setFeedback(feedback);

        return evalRepository.save(record);
    }

    // ============================================================
    // 查询（严格用户隔离）
    // ============================================================

    public List<AgentEvaluationRecord> findByUserId(Long userId) {
        if (userId == null) return new ArrayList<>();
        return evalRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<AgentEvaluationRecord> findByUserIdAndAgentType(Long userId, String agentType) {
        if (userId == null) return new ArrayList<>();
        return evalRepository.findByUserIdAndAgentType(userId, agentType);
    }

    public List<AgentEvaluationRecord> findByUserIdAndEvaluationType(Long userId, String evalType) {
        if (userId == null) return new ArrayList<>();
        return evalRepository.findByUserIdAndEvaluationType(userId, evalType);
    }

    public Optional<AgentEvaluationRecord> findByIdAndUserId(Long id, Long userId) {
        if (id == null || userId == null) return Optional.empty();
        return evalRepository.findByIdAndUserId(id, userId);
    }

    // ============================================================
    // 删除
    // ============================================================

    @Transactional
    public boolean deleteByIdAndUserId(Long id, Long userId) {
        if (id == null || userId == null) return false;
        int affected = evalRepository.deleteByIdAndUserId(id, userId);
        boolean ok = affected > 0;
        log.info("deleteByIdAndUserId userId={} id={} affected={}", userId, id, affected);
        return ok;
    }

    // ============================================================
    // Grounding 检查 / RAG 评估
    // ============================================================

    public GroundingResult checkGrounding(Long userId, String answer, String memoryContext, String ragContext) {
        return groundingChecker.check(answer, memoryContext, ragContext, userId);
    }

    public RAGMetrics evaluateRAG(Long userId, String question, String retrievedContext, String answer) {
        return ragEvaluator.evaluate(question, retrievedContext, answer, userId);
    }

    // ============================================================
    // 统计 / Dashboard 查询
    // ============================================================

    public List<Object[]> getAgentScoreRanking(Long userId) {
        if (userId == null) return new ArrayList<>();
        return evalRepository.findAgentScoreRanking(userId);
    }

    public List<AgentEvaluationRecord> getRecentScored(Long userId, int limit) {
        if (userId == null) return new ArrayList<>();
        List<AgentEvaluationRecord> list = evalRepository.findRecentScored(userId);
        if (list == null) return new ArrayList<>();
        return list.subList(0, Math.min(limit, list.size()));
    }

    public List<Object[]> getPromptVersionComparison(Long userId, String agentType) {
        if (userId == null) return new ArrayList<>();
        return evalRepository.findPromptVersionComparison(userId, agentType);
    }

    public List<AgentEvaluationRecord> getRecentByAgent(Long userId, String agentType, int limit) {
        if (userId == null) return new ArrayList<>();
        List<AgentEvaluationRecord> list = evalRepository.findRecentByAgent(userId, agentType);
        if (list == null) return new ArrayList<>();
        return list.subList(0, Math.min(limit, list.size()));
    }

    // ============================================================
    // Prompt 版本管理（全局共享，非用户隔离）
    // ============================================================

    @Transactional
    public PromptVersion createPromptVersion(String agentType, String version, String promptContent,
                                             String description, Boolean enabled) {
        boolean enable = Boolean.TRUE.equals(enabled);
        if (enable) {
            promptVersionRepository.disableAllByAgentType(agentType);
        }
        PromptVersion pv = PromptVersion.builder()
                .agentType(agentType)
                .version(version)
                .promptContent(promptContent)
                .description(description)
                .enabled(enable)
                .avgScore(0.0)
                .evalCount(0)
                .build();
        return promptVersionRepository.save(pv);
    }

    public List<PromptVersion> findPromptVersionsByAgent(String agentType) {
        if (agentType == null) return new ArrayList<>();
        return promptVersionRepository.findByAgentTypeOrderByCreatedAtDesc(agentType);
    }

    @Transactional
    public Optional<PromptVersion> enablePromptVersion(Long id) {
        if (id == null) return Optional.empty();
        Optional<PromptVersion> opt = promptVersionRepository.findById(id);
        if (opt.isEmpty()) {
            return Optional.empty();
        }
        PromptVersion pv = opt.get();
        promptVersionRepository.disableAllByAgentType(pv.getAgentType());
        pv.setEnabled(true);
        PromptVersion saved = promptVersionRepository.save(pv);
        return Optional.of(saved);
    }

    /**
     * 更新 Prompt 版本的平均评估得分（运行平均）：
     * newAvg = (oldAvg * oldCount + score) / (oldCount + 1)，evalCount + 1
     */
    @Transactional
    public void updatePromptVersionScore(String agentType, String version, Integer score) {
        if (agentType == null || version == null || score == null) return;
        Optional<PromptVersion> opt = promptVersionRepository.findByAgentTypeAndVersion(agentType, version);
        if (opt.isEmpty()) {
            log.debug("updatePromptVersionScore skipped: agentType={} version={} not found", agentType, version);
            return;
        }
        PromptVersion pv = opt.get();
        double oldAvg = pv.getAvgScore() == null ? 0.0 : pv.getAvgScore();
        int oldCount = pv.getEvalCount() == null ? 0 : pv.getEvalCount();
        double newAvg = (oldAvg * oldCount + score) / (oldCount + 1);
        pv.setAvgScore(newAvg);
        pv.setEvalCount(oldCount + 1);
        promptVersionRepository.save(pv);
    }

    /**
     * 回填评估分数到最近一条 LLM 调用日志（Cost / Quality Tradeoff 分析）
     */
    @Transactional
    public void attachScoreToLLMLog(Long logId, Integer score) {
        if (logId == null || score == null) return;
        llmLogRepository.updateEvaluationScore(logId, score);
    }
}
