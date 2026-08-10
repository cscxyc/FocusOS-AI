package com.focusos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.focusos.repository.LLMCallLogRepository;
import dev.langchain4j.model.chat.ChatLanguageModel;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Sprint 8-E: LLM 统一网关
 * <p>
 * 职责：所有 Agent 调用 LLM 的统一入口，提供模型路由、超时控制、Fallback、成本估算与配额管控。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>直接使用已注入的 {@link ChatLanguageModel}（其已被 {@code LoggingChatLanguageModel} 装饰，
 *       调用 {@link ChatLanguageModel#chat(String)} 时会自动记录调用日志，无需网关重复记录）</li>
 *   <li>配额：通过 {@code ObjectProvider<QuotaService>} 注入，QuotaService 实现不存在时跳过配额检查</li>
 *   <li>成本估算：inputTokens = prompt.length()/4，outputTokens = response.length()/4；
 *       estimatedCost = inputTokens/1000 * costPer1kInput + outputTokens/1000 * costPer1kOutput</li>
 *   <li>指标：通过 {@code ObjectProvider<MeterRegistry>} 记录 {@code llm.gateway.latency} Timer 与 {@code llm.gateway.calls} Counter</li>
 * </ul>
 * <p>
 * 模型路由（{@code focusos.llm-gateway.routing}）：当前简化处理，统一使用注入的 {@link ChatLanguageModel}，
 * 后续可按 agentType 路由到不同模型实例。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LLMGateway {

    private final ChatLanguageModel chatLanguageModel;
    private final ObjectProvider<QuotaService> quotaServiceProvider;
    private final LLMCallLogRepository llmCallLogRepository;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    /** LLM 调用超时（秒），仅用于日志参考，实际超时由 ChatLanguageModel 底层客户端控制 */
    @Value("${focusos.llm-gateway.timeout-seconds:60}")
    private int timeoutSeconds;

    /** 每 1000 输入 Token 的成本（用于估算） */
    @Value("${focusos.quota.cost-per-1k-input-tokens:0.004}")
    private double costPer1kInput;

    /** 每 1000 输出 Token 的成本（用于估算） */
    @Value("${focusos.quota.cost-per-1k-output-tokens:0.012}")
    private double costPer1kOutput;

    /** 当前使用的模型名称（用于填充 ChatResponse.model，简化路由下统一取基础模型名） */
    @Value("${focusos.ai.model:unknown}")
    private String modelName;

    /**
     * 统一调用入口。
     * <p>
     * 流程：
     * <ol>
     *   <li>配额检查：{@link QuotaService#checkQuota}，超额抛出 {@link QuotaExceededException}（向上传播，不作为调用失败）</li>
     *   <li>调用 {@code chatLanguageModel.chat(prompt)}（装饰器自动记录日志）</li>
     *   <li>估算 token、计算成本、记录配额使用、记录指标</li>
     *   <li>异常时返回 {@code success=false} 的 {@link ChatResponse}</li>
     * </ol>
     *
     * @param agentType Agent 类型（career / interview / resume_evaluator ...）
     * @param prompt    输入提示词
     * @param userId    用户 ID
     * @return 调用结果（含内容、token、成本、延迟等）
     * @throws QuotaExceededException 当用户配额超限时抛出
     */
    public ChatResponse call(String agentType, String prompt, Long userId) {
        // 1. 配额检查（QuotaService 实现不存在时跳过）
        QuotaService quotaService = quotaServiceProvider.getIfAvailable();
        if (quotaService != null && userId != null) {
            quotaService.checkQuota(userId); // 超额抛出 QuotaExceededException，直接向上传播
        }

        long start = System.currentTimeMillis();
        try {
            // 2. 调用 LLM（已由 LoggingChatLanguageModel 装饰，自动记录调用日志）
            String response = chatLanguageModel.chat(prompt);
            long latencyMs = System.currentTimeMillis() - start;

            // 3. 估算 Token：inputTokens = prompt.length()/4, outputTokens = response.length()/4
            int inputTokens = estimateTokens(prompt);
            int outputTokens = estimateTokens(response);

            // 4. 计算成本
            double estimatedCost = (inputTokens / 1000.0) * costPer1kInput
                    + (outputTokens / 1000.0) * costPer1kOutput;

            // 5. 记录配额使用
            if (quotaService != null && userId != null) {
                try {
                    quotaService.recordUsage(userId, inputTokens + outputTokens);
                } catch (Exception ex) {
                    log.warn("记录配额使用失败（不影响主流程）: {}", ex.getMessage());
                }
            }

            // 6. 记录 Micrometer 指标
            recordMetrics(agentType, latencyMs, true);

            return ChatResponse.builder()
                    .content(response)
                    .model(modelName)
                    .inputTokens(inputTokens)
                    .outputTokens(outputTokens)
                    .latencyMs(latencyMs)
                    .success(true)
                    .errorMessage(null)
                    .estimatedCost(estimatedCost)
                    .fallbackUsed(false)
                    .build();
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - start;
            log.error("LLM 调用失败 (agentType={}, userId={}): {}", agentType, userId, e.getMessage());
            recordMetrics(agentType, latencyMs, false);
            return ChatResponse.builder()
                    .content(null)
                    .model(modelName)
                    .inputTokens(estimateTokens(prompt))
                    .outputTokens(0)
                    .latencyMs(latencyMs)
                    .success(false)
                    .errorMessage(e.getMessage())
                    .estimatedCost(0.0)
                    .fallbackUsed(false)
                    .build();
        }
    }

    /**
     * 带 Fallback 的调用。
     * <p>
     * 流程：
     * <ol>
     *   <li>执行主调用，成功则直接返回</li>
     *   <li>主调用失败 → 用简化后的 prompt 重试一次（截断过长输入，降低负载）</li>
     *   <li>重试仍失败 → 返回降级响应（提示 LLM 不可用），{@code fallbackUsed=true}</li>
     * </ol>
     * <p>
     * 注意：配额超限（{@link QuotaExceededException}）会从 {@link #call} 直接向上传播，不触发 Fallback 重试。
     *
     * @param agentType Agent 类型
     * @param prompt    输入提示词
     * @param userId    用户 ID
     * @return 调用结果（可能为降级响应）
     */
    public ChatResponse callWithFallback(String agentType, String prompt, Long userId) {
        ChatResponse primary = call(agentType, prompt, userId);
        if (primary.isSuccess()) {
            return primary;
        }

        log.warn("主调用失败，尝试简化 prompt 重试 (agentType={}): {}", agentType, primary.getErrorMessage());
        String simplifiedPrompt = simplifyPrompt(prompt);
        ChatResponse retry = call(agentType, simplifiedPrompt, userId);
        if (retry.isSuccess()) {
            retry.setFallbackUsed(true);
            return retry;
        }

        // 仍然失败，返回降级响应
        log.error("Fallback 重试仍失败，返回降级响应 (agentType={}): {}", agentType, retry.getErrorMessage());
        return ChatResponse.builder()
                .content("【降级响应】LLM 服务暂时不可用，请稍后重试。")
                .model(modelName)
                .inputTokens(0)
                .outputTokens(0)
                .latencyMs(0L)
                .success(false)
                .errorMessage("LLM 不可用，已降级")
                .estimatedCost(0.0)
                .fallbackUsed(true)
                .build();
    }

    /** Token 估算：约 4 字符 / token */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, text.length() / 4);
    }

    /** 简化 prompt：截断过长输入，降低负载，便于 Fallback 重试 */
    private String simplifyPrompt(String prompt) {
        if (prompt == null) {
            return "";
        }
        int maxLen = 800;
        if (prompt.length() <= maxLen) {
            return prompt;
        }
        return prompt.substring(0, maxLen) + "\n...(已截断)";
    }

    /** 记录 Micrometer 指标（MeterRegistry 不存在时跳过） */
    private void recordMetrics(String agentType, long latencyMs, boolean success) {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry == null) {
            return;
        }
        try {
            String agent = agentType != null ? agentType : "unknown";
            String result = success ? "success" : "failure";
            Timer.builder("llm.gateway.latency")
                    .tag("agent", agent)
                    .tag("result", result)
                    .register(registry)
                    .record(latencyMs, TimeUnit.MILLISECONDS);
            registry.counter("llm.gateway.calls", "agent", agent, "result", result)
                    .increment();
        } catch (Exception e) {
            log.debug("记录 LLM 网关指标失败: {}", e.getMessage());
        }
    }

    /**
     * LLM 调用统一响应。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatResponse {
        /** LLM 返回的文本内容（失败时为 null，降级时为提示文案） */
        private String content;
        /** 实际使用的模型名称 */
        private String model;
        /** 输入 token 数（估算） */
        private int inputTokens;
        /** 输出 token 数（估算） */
        private int outputTokens;
        /** 调用耗时（毫秒） */
        private long latencyMs;
        /** 是否成功 */
        private boolean success;
        /** 失败时的错误信息 */
        private String errorMessage;
        /** 估算成本（美元） */
        private double estimatedCost;
        /** 是否使用了 Fallback（简化 prompt 重试或降级响应） */
        private boolean fallbackUsed;
    }
}
