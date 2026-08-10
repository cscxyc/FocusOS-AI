package com.focusos.service;

import lombok.Getter;

/**
 * Sprint 8-E: 用户配额超限异常
 * <p>
 * 当用户当日 Token 用量超过配额上限（{@code focusos.quota.default-daily-token-limit} /
 * {@code focusos.quota.premium-daily-token-limit}）时由 {@link QuotaService#checkQuota} 抛出。
 * <p>
 * {@link LLMGateway#call} 在调用 LLM 前执行配额检查，超限时直接向上抛出本异常，
 * 由全局异常处理器或调用方决定如何向用户反馈（HTTP 429 等）。
 */
@Getter
public class QuotaExceededException extends RuntimeException {

    /** 触发配额超限的用户 ID */
    private final Long userId;

    /** 当前已使用的 Token 数量 */
    private final long usedTokens;

    public QuotaExceededException(Long userId, long usedTokens) {
        super(String.format("用户 %d 的 Token 配额已超限，已使用 %d tokens", userId, usedTokens));
        this.userId = userId;
        this.usedTokens = usedTokens;
    }

    public QuotaExceededException(Long userId, long usedTokens, String message) {
        super(message);
        this.userId = userId;
        this.usedTokens = usedTokens;
    }
}
