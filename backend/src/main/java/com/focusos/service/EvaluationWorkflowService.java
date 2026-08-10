package com.focusos.service;

import com.focusos.entity.AgentEvaluationRecord;
import com.focusos.entity.AgentTask;
import com.focusos.repository.AgentEvaluationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Sprint 8-D: 评估工作流服务
 * <p>
 * 职责：在工作流执行完成后，对其中各 Agent 的输出进行自动评估。
 * 评估失败不影响主工作流，仅记录日志。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationWorkflowService {

    private final AgentEvaluationService evaluationService;
    private final AgentEvaluationRepository evalRepository;

    /** agentType → evaluationType 映射 */
    private static final Map<String, String> AGENT_EVAL_TYPE_MAP = Map.of(
            "career", "CAREER_ANALYSIS",
            "resume_evaluator", "RESUME_GENERATION",
            "interview", "INTERVIEW",
            "rag", "RAG_RETRIEVAL",
            "memory", "MEMORY_EXTRACTION",
            "career_growth", "GROWTH_PLAN"
    );

    /**
     * 对一个工作流中的全部已完成任务做评估。
     * 每个任务独立 try-catch，单个任务评估失败不影响其他任务，也不抛出异常。
     *
     * @param userId     用户 ID
     * @param workflowId 工作流 ID
     * @param tasks      工作流包含的全部任务
     * @param context    附加上下文（可选，目前未使用）
     */
    public void evaluateWorkflow(Long userId, String workflowId, List<AgentTask> tasks, String context) {
        if (tasks == null || tasks.isEmpty()) {
            log.debug("evaluateWorkflow skipped: no tasks for workflowId={}", workflowId);
            return;
        }
        int evaluated = 0;
        int skipped = 0;
        int failed = 0;
        for (AgentTask task : tasks) {
            if (task == null) continue;
            // 仅评估已完成（SUCCESS）且具有输出的任务
            if (!"SUCCESS".equalsIgnoreCase(task.getStatus())) {
                skipped++;
                continue;
            }
            String agentType = task.getAgentType();
            String evaluationType = AGENT_EVAL_TYPE_MAP.get(agentType);
            if (evaluationType == null) {
                log.debug("evaluateWorkflow skipped task id={}: agentType={} 无评估类型映射", task.getId(), agentType);
                skipped++;
                continue;
            }
            String input = task.getGoal();
            String output = task.getResult();
            if (output == null || output.isBlank()) {
                skipped++;
                continue;
            }
            try {
                AgentEvaluationRecord record = evaluationService.evaluateAndSave(
                        userId, agentType, evaluationType, input, output, workflowId, null);
                // 若任务携带 promptVersion 可在此回填，此处暂不处理
                evaluated++;
                log.debug("evaluateWorkflow task id={} agentType={} evaluated score={}",
                        task.getId(), agentType, record != null ? record.getScore() : null);
            } catch (Exception e) {
                failed++;
                log.error("evaluateWorkflow task id={} agentType={} 评估失败（不影响工作流）",
                        task.getId(), agentType, e);
            }
        }
        log.info("evaluateWorkflow done workflowId={} userId={} total={} evaluated={} skipped={} failed={}",
                workflowId, userId, tasks.size(), evaluated, skipped, failed);
    }

    /**
     * 便捷方法：对单个 Agent 输出做评估
     */
    public void evaluateSingleAgentOutput(Long userId, String agentType, String evaluationType,
                                          String input, String output, String workflowId) {
        evaluationService.evaluateAndSave(userId, agentType, evaluationType, input, output, workflowId, null);
    }
}
