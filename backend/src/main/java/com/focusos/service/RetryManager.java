package com.focusos.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * Sprint 8-E: LLM 调用重试框架
 * <p>
 * 在 Agent 执行 / LLM 调用失败时按 {@link RetryPolicy} 自动重试，配合退避等待，
 * 重试次数用尽后抛出异常，由调用方（如 {@link AgentWorker}）处理 fallback 或标记失败。
 * <p>
 * 默认退避策略：第 1 次失败等待 2s，第 2 次失败等待 5s，第 3 次失败直接抛出异常。
 */
@Slf4j
@Service
public class RetryManager {

    /**
     * 重试策略
     * <ul>
     *   <li>maxRetry：最大尝试次数（含首次执行，默认 3）</li>
     *   <li>backoffMs：每次失败后的退避等待时长（毫秒），默认 {2000, 5000, 10000}</li>
     *   <li>fallbackModel：备用模型标识（由调用方在最终失败时使用，默认 null）</li>
     * </ul>
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RetryPolicy {
        @Builder.Default
        private int maxRetry = 3;

        @Builder.Default
        private long[] backoffMs = {2000L, 5000L, 10000L};

        @Builder.Default
        private String fallbackModel = null;
    }

    /**
     * 执行带重试的任务。
     * <p>
     * 默认流程（{@code maxRetry=3}）：
     * <pre>
     *   第 1 次尝试失败 → 等待 backoffMs[0]=2s → 重试
     *   第 2 次尝试失败 → 等待 backoffMs[1]=5s → 重试
     *   第 3 次尝试失败 → 抛出 RuntimeException（由调用方处理 fallback）
     * </pre>
     *
     * @param task   待执行任务
     * @param policy 重试策略
     * @param <T>    返回值类型
     * @return 任务执行结果
     * @throws RuntimeException 重试次数用尽后抛出，cause 为最后一次异常
     */
    public <T> T executeWithRetry(Supplier<T> task, RetryPolicy policy) {
        Exception lastException = null;
        int maxRetry = policy.getMaxRetry();

        for (int attempt = 1; attempt <= maxRetry; attempt++) {
            try {
                return task.get();
            } catch (Exception e) {
                lastException = e;
                log.warn("任务第 {}/{} 次执行失败: {}", attempt, maxRetry, e.getMessage());
                if (attempt >= maxRetry) {
                    // 达到最大尝试次数（第 N 次失败），抛出异常由调用方处理 fallback
                    break;
                }
                long backoff = backoffFor(policy, attempt);
                log.info("等待 {}ms 后重试...", backoff);
                sleep(backoff);
            }
        }
        throw new RuntimeException("任务在 " + maxRetry + " 次尝试后仍失败", lastException);
    }

    /** 返回默认重试策略（maxRetry=3, backoff={2s,5s,10s}） */
    public RetryPolicy defaultPolicy() {
        return RetryPolicy.builder().build();
    }

    /**
     * 根据第几次失败（1-based）取出对应的退避时长，越界则取数组最后一个值。
     */
    private long backoffFor(RetryPolicy policy, int failureCount) {
        long[] backoff = policy.getBackoffMs();
        if (backoff == null || backoff.length == 0) {
            return 2000L;
        }
        int idx = Math.min(failureCount - 1, backoff.length - 1);
        return backoff[idx];
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("重试等待被中断", e);
        }
    }
}
