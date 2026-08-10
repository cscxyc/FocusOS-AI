package com.focusos.agent;

/**
 * Sprint 7-C-B: LLM 调用上下文（ThreadLocal）
 * <p>
 * 用于在 Service 层设置当前 LLM 调用的上下文信息（userId / workflowId / agentType），
 * LoggingChatLanguageModel 装饰器会从 ThreadLocal 读取这些信息写入 llm_call_logs。
 * <p>
 * 使用方式：
 * <pre>
 * LLMCallContext.set(userId, workflowId, "career");
 * try {
 *     String result = agent.analyzeCareerStructured(...);
 * } finally {
 *     LLMCallContext.clear();
 * }
 * </pre>
 * <p>
 * 注意：异步线程（CompletableFuture）不会继承 ThreadLocal，
 * 因此必须在异步任务内部（而非提交任务前）设置上下文。
 */
public final class LLMCallContext {

    private static final ThreadLocal<Context> HOLDER = new ThreadLocal<>();

    private LLMCallContext() {}

    public static void set(Long userId, String workflowId, String agentType) {
        HOLDER.set(new Context(userId, workflowId, agentType));
    }

    public static Context get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public record Context(Long userId, String workflowId, String agentType) {}
}
