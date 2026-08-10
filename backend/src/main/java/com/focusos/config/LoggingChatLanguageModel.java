package com.focusos.config;

import com.focusos.service.LLMLoggingService;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Sprint 7-C-B: LLM 调用监控装饰器
 * <p>
 * 包装真实的 ChatLanguageModel，在每次 chat() 调用前后自动记录：
 * - 开始时间 / 结束时间 → latencyMs
 * - 输入文本 / 输出文本 → 估算 inputTokens / outputTokens
 * - 成功 / 异常 → success + errorMessage
 * - 从 LLMCallContext (ThreadLocal) 读取 userId / workflowId / agentType
 * <p>
 * Agent 代码无需任何修改，只需在 LlmConfig 中用此装饰器包装原始 Bean。
 */
@Slf4j
public class LoggingChatLanguageModel implements ChatLanguageModel {

    private final ChatLanguageModel delegate;
    private final LLMLoggingService loggingService;
    private final String modelName;

    public LoggingChatLanguageModel(ChatLanguageModel delegate, LLMLoggingService loggingService, String modelName) {
        this.delegate = delegate;
        this.loggingService = loggingService;
        this.modelName = modelName;
    }

    @Override
    public String chat(String prompt) {
        long start = System.currentTimeMillis();
        try {
            String response = delegate.chat(prompt);
            long latency = System.currentTimeMillis() - start;
            loggingService.recordCall(modelName, prompt, response, latency, true, null);
            return response;
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            loggingService.recordCall(modelName, prompt, null, latency, false, e.getMessage());
            throw e;
        }
    }

    @Override
    public ChatResponse chat(List<ChatMessage> messages) {
        long start = System.currentTimeMillis();
        StringBuilder inputSb = new StringBuilder();
        for (ChatMessage msg : messages) {
            inputSb.append(msg.text()).append("\n");
        }
        String inputText = inputSb.toString();
        try {
            ChatResponse response = delegate.chat(messages);
            long latency = System.currentTimeMillis() - start;
            String outputText = response != null && response.aiMessage() != null ? response.aiMessage().text() : "";
            loggingService.recordCall(modelName, inputText, outputText, latency, true, null);
            return response;
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            loggingService.recordCall(modelName, inputText, null, latency, false, e.getMessage());
            throw e;
        }
    }
}
