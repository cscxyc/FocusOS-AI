package com.focusos.controller;

import com.focusos.agent.MasterAgent;
import com.focusos.dto.response.ApiResponse;
import com.focusos.entity.User;
import com.focusos.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 统一 AI Agent 入口
 */
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class AgentController {

    private final MasterAgent masterAgent;
    private final ConversationService conversationService;

    /**
     * 显式路由：指定 agentType 调用对应 Agent
     */
    @PostMapping("/chat")
    public ApiResponse<Map<String, Object>> chat(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, Object> request) {

        String message = (String) request.get("message");
        String agentType = (String) request.getOrDefault("agentType", "learning");

        // 通过 ConversationService 保存对话并获取上下文
        String response = conversationService.chatWithAgent(
                user.getId(), message, agentType);

        return ApiResponse.success("AI回复成功", Map.of(
                "response", response,
                "agentType", agentType
        ));
    }

    /**
     * 智能路由：LLM 自动判断意图并分发
     */
    @PostMapping("/smart-chat")
    public ApiResponse<Map<String, Object>> smartChat(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, Object> request) {

        String message = (String) request.get("message");

        String response = conversationService.smartChatWithAgent(
                user.getId(), message);

        return ApiResponse.success("AI回复成功", Map.of(
                "response", response
        ));
    }
}
