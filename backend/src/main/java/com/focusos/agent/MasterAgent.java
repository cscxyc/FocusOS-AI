package com.focusos.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.focusos.dto.response.WorkflowResponse;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Master Agent — 统一 AI 入口
 * <p>
 * Sprint 6-A 升级：从单纯路由器升级为 AI 任务规划器
 * - 单轮对话：通过 AgentRouter 分发到子 Agent
 * - 多 Agent 协作：通过 AgentWorkflowService 进行任务拆解与链式执行
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MasterAgent {

    private final ChatLanguageModel chatLanguageModel;
    private final AgentRouter agentRouter;
    private final ObjectMapper objectMapper;
    private final AgentPromptProvider promptProvider;
    private final AgentWorkflowService agentWorkflowService;

    /**
     * Dashboard 推荐 — 不走路由，直接调用 LLM
     */
    public String provideRecommendation(Map<String, Object> dashboardData) {
        try {
            String dataJson = objectMapper.writeValueAsString(dashboardData);

            String prompt = String.format("""
                    你是FocusOS AI的智能助手。请根据用户的以下数据，提供个性化的学习和职业建议：

                    用户数据：%s

                    请提供：
                    1. 学习状态评估
                    2. 今日学习建议
                    3. 职业发展建议
                    4. 时间管理优化建议
                    5. 动力激励话语

                    请用中文回答，语气亲切、专业、具有指导性。控制在200字以内。
                    """, dataJson);

            String systemPrompt = """
                    你是 FocusOS AI 平台的智能助手，擅长根据用户数据提供个性化建议。
                    输出要求：
                    1. 使用中文
                    2. 语气亲切、专业、具有指导性
                    3. 控制在200字以内
                    4. 内容具体、可操作
                    """;

            return chatLanguageModel.chat(systemPrompt + "\n\n" + prompt);
        } catch (Exception e) {
            log.error("Failed to provide recommendation", e);
            return "智能建议生成失败，请稍后重试。";
        }
    }

    /**
     * 统一 AI 入口 — 显式指定 Agent 类型
     */
    public String routeToAgent(String agentType, String message, Long userId, String context) {
        FocusAgent agent = agentRouter.route(agentType);
        return agent.handle(message, userId, context);
    }

    /**
     * 统一 AI 入口 — 智能路由（LLM 意图识别）
     */
    public String smartRoute(String message, Long userId, String context) {
        FocusAgent agent = agentRouter.smartRoute(message);
        if (agent == null) {
            return chatLanguageModel.chat(message);
        }
        return agent.handle(message, userId, context);
    }

    /**
     * Sprint 6-B: 异步启动 Multi-Agent Workflow（立即返回 workflowId）
     * <p>
     * 调用链：用户目标 → 任务规划 → @Async 异步执行 + SSE 实时推送
     */
    public String startWorkflowAsync(Long userId, String userGoal) {
        return agentWorkflowService.startWorkflowAsync(userId, userGoal);
    }

    /**
     * Sprint 6-A: 获取用户工作流历史
     */
    public java.util.List<WorkflowResponse> getUserWorkflows(Long userId) {
        return agentWorkflowService.getUserWorkflows(userId);
    }
}
