package com.focusos.service;

import com.focusos.agent.MasterAgent;
import com.focusos.entity.ConversationMessage;
import com.focusos.repository.ConversationMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 对话记忆服务 — 管理用户对话历史、构造上下文、保存消息
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationMessageRepository conversationMessageRepository;
    private final MasterAgent masterAgent;

    private static final int MAX_MESSAGES = 20;
    private static final int MAX_CONTEXT_CHARS = 6000;

    /**
     * 带对话记忆的 Agent 调用（显式路由）
     */
    @Transactional
    public String chatWithAgent(Long userId, String message, String agentType) {
        // 1. 查询历史对话
        List<ConversationMessage> history = conversationMessageRepository
                .findByUserIdAndAgentTypeOrderByCreatedAtAsc(userId, agentType);

        // 2. 构造上下文
        String context = buildContext(history);

        // 3. 保存用户消息
        saveMessage(userId, agentType, "user", message);

        // 4. 调用 Agent
        String response = masterAgent.routeToAgent(agentType, message, userId, context);

        // 5. 保存 AI 回复
        saveMessage(userId, agentType, "assistant", response);

        return response;
    }

    /**
     * 带对话记忆的 Agent 调用（智能路由）
     */
    @Transactional
    public String smartChatWithAgent(Long userId, String message) {
        // 智能路由暂时使用 "default" 作为 agentType
        List<ConversationMessage> history = conversationMessageRepository
                .findByUserIdOrderByCreatedAtAsc(userId);

        String context = buildContext(history);

        saveMessage(userId, "default", "user", message);

        String response = masterAgent.smartRoute(message, userId, context);

        saveMessage(userId, "default", "assistant", response);

        return response;
    }

    /**
     * 获取用户对话历史
     */
    public List<ConversationMessage> getHistory(Long userId) {
        return conversationMessageRepository.findByUserIdOrderByCreatedAtAsc(userId);
    }

    /**
     * 获取指定 Agent 类型的对话历史
     */
    public List<ConversationMessage> getHistoryByAgentType(Long userId, String agentType) {
        return conversationMessageRepository.findByUserIdAndAgentTypeOrderByCreatedAtAsc(userId, agentType);
    }

    /**
     * 清除用户对话历史
     */
    @Transactional
    public void clearHistory(Long userId) {
        conversationMessageRepository.deleteByUserId(userId);
        log.info("Cleared conversation history for user: {}", userId);
    }

    /**
     * 构造对话上下文（最近 N 条消息 + 字符上限双重限制）
     */
    private String buildContext(List<ConversationMessage> history) {
        if (history == null || history.isEmpty()) {
            return "无";
        }

        int start = Math.max(0, history.size() - MAX_MESSAGES);
        List<ConversationMessage> recent = history.subList(start, history.size());

        List<String> lines = new ArrayList<>();
        int totalChars = 0;
        for (ConversationMessage msg : recent) {
            String line = String.format("%s: %s",
                    msg.getRole().equals("user") ? "用户" : "AI",
                    msg.getContent());
            if (totalChars + line.length() >= MAX_CONTEXT_CHARS) {
                break;
            }
            lines.add(line);
            totalChars += line.length() + 1;
        }

        return String.join("\n", lines);
    }

    private void saveMessage(Long userId, String agentType, String role, String content) {
        ConversationMessage msg = new ConversationMessage();
        msg.setUserId(userId);
        msg.setAgentType(agentType);
        msg.setRole(role);
        msg.setContent(content);
        conversationMessageRepository.save(msg);
    }
}
