package com.focusos.service;

/**
 * Sprint 8-E: 用户配额服务契约
 * <p>
 * 职责：
 * <ol>
 *   <li>{@link #checkQuota} — 在调用 LLM 前检查用户当日 Token 配额是否超限，超限抛出 {@link QuotaExceededException}</li>
 *   <li>{@link #recordUsage} — 在 LLM 调用成功后记录用户实际 Token 消耗（input + output）</li>
 * </ol>
 * <p>
 * 注意：当前仅为接口契约，<b>尚未提供实现 Bean</b>（"暂时还不存在"）。
 * {@link LLMGateway} 通过 {@code ObjectProvider<QuotaService>} 注入：
 * <ul>
 *   <li>当前无实现 Bean → {@code getIfAvailable()} 返回 {@code null} → 配额检查被跳过（不限制）</li>
 *   <li>后续 Sprint 新增 {@code @Service class QuotaServiceImpl implements QuotaService} 即可自动激活配额管控</li>
 * </ul>
 * 这样既保证 {@link LLMGateway} 现在可编译、可启动，又为未来配额实现预留了无缝接入点，无需修改现有文件。
 */
public interface QuotaService {

    /**
     * 检查用户当日 Token 配额。
     *
     * @param userId 用户 ID
     * @throws QuotaExceededException 当用户已用量超过配额上限时抛出
     */
    void checkQuota(Long userId);

    /**
     * 记录用户本次 LLM 调用的 Token 消耗。
     *
     * @param userId 用户 ID
     * @param tokens 本次调用消耗的 Token 数（inputTokens + outputTokens）
     */
    void recordUsage(Long userId, int tokens);
}
