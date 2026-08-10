package com.focusos.agent;

/**
 * 统一 Agent 接口，所有子 Agent 实现此接口
 */
public interface FocusAgent {

    /**
     * Agent 类型标识，用于路由匹配
     */
    String type();

    /**
     * 处理用户消息
     *
     * @param message      用户消息
     * @param userId       用户ID（用于数据隔离）
     * @param context      对话上下文（历史消息摘要）
     * @return AI 回复内容
     */
    String handle(String message, Long userId, String context);
}
