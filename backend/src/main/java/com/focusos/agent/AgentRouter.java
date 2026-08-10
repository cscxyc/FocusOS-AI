package com.focusos.agent;

import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Agent 路由器 — 根据用户消息自动选择最合适的 Agent
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRouter {

    private final AgentRegistry agentRegistry;
    private final ChatLanguageModel chatLanguageModel;
    private final AgentPromptProvider promptProvider;

    /**
     * 显式路由：按 agentType 直接分发
     */
    public FocusAgent route(String agentType) {
        return agentRegistry.getAgent(agentType)
                .orElseThrow(() -> new IllegalArgumentException("未知Agent类型: " + agentType));
    }

    /**
     * 智能路由：通过 LLM 意图识别自动选择 Agent
     */
    public FocusAgent smartRoute(String message) {
        try {
            String prompt = promptProvider.routerSystemPrompt() + "\n\n用户消息: " + message;
            String result = chatLanguageModel.chat(prompt).trim().toLowerCase();

            // 提取第一个匹配的 agent type
            for (String type : agentRegistry.getAllAgents().keySet()) {
                if (result.contains(type)) {
                    log.info("Smart routed to agent: {} for message: {}", type, message.substring(0, Math.min(50, message.length())));
                    return agentRegistry.getAgent(type).orElse(null);
                }
            }
        } catch (Exception e) {
            log.error("Smart route failed, falling back to default", e);
        }

        // 降级：默认路由到 learning agent
        return agentRegistry.getAgent("learning").orElse(null);
    }
}