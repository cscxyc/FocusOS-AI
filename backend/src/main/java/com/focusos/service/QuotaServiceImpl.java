package com.focusos.service;

import com.focusos.entity.UserQuota;
import com.focusos.entity.UserQuota.Tier;
import com.focusos.repository.UserQuotaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Sprint 8-E: 用户配额服务实现 (Task 8)
 * <p>
 * 在 {@link LLMGateway#call} 调用 LLM 前进行配额检查，超限抛出 {@link QuotaExceededException}；
 * 调用成功后通过 {@link #recordUsage} 累加用户当日 Token 使用量。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>首次访问自动初始化：用户首次调用时按 {@code Tier.DEFAULT} 创建配额记录</li>
 *   <li>跨日自动重置：当日 {@code LocalDate.now() > quota.resetDate} 时自动重置 usedTokens=0</li>
 *   <li>原子累加：通过 {@code incrementUsedTokens} 原子 SQL 避免并发竞争</li>
 *   <li>独立事务：{@code REQUIRES_NEW} 防止配额失败回滚主调用事务</li>
 *   <li>降级容错：配额服务本身异常时记录告警但不阻塞主流程（避免配额系统故障导致整体不可用）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaServiceImpl implements QuotaService {

    private final UserQuotaRepository quotaRepository;

    /** 普通用户每日 Token 上限（默认 10000） */
    @Value("${focusos.quota.default-daily-token-limit:10000}")
    private long defaultDailyTokenLimit;

    /** 高级用户每日 Token 上限（默认 100000） */
    @Value("${focusos.quota.premium-daily-token-limit:100000}")
    private long premiumDailyTokenLimit;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkQuota(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            UserQuota quota = getOrCreateQuota(userId);
            // 跨日重置
            quota = resetIfStale(quota);

            long used = quota.getUsedTokens() != null ? quota.getUsedTokens() : 0L;
            long limit = quota.getDailyTokenLimit() != null ? quota.getDailyTokenLimit() : defaultDailyTokenLimit;

            if (used >= limit) {
                log.warn("用户 {} 当日 Token 配额超限：used={}, limit={}", userId, used, limit);
                throw new QuotaExceededException(userId, used,
                        String.format("Daily token quota exceeded (used=%d, limit=%d)", used, limit));
            }
        } catch (QuotaExceededException e) {
            throw e; // 配额超限正常向上抛出
        } catch (Exception e) {
            // 配额服务自身故障时降级（不阻塞主流程），仅记录告警
            log.warn("配额检查失败 userId={}, 降级跳过: {}", userId, e.getMessage());
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordUsage(Long userId, int tokens) {
        if (userId == null || tokens <= 0) {
            return;
        }
        try {
            // 先确保配额记录存在
            getOrCreateQuota(userId);
            int updated = quotaRepository.incrementUsedTokens(userId, tokens);
            if (updated == 0) {
                // 并发场景：记录被删除/未创建成功，重新创建并直接累加
                UserQuota quota = getOrCreateQuota(userId);
                quota.setUsedTokens((quota.getUsedTokens() != null ? quota.getUsedTokens() : 0L) + tokens);
                quotaRepository.save(quota);
            }
            log.debug("用户 {} Token 使用量累加 {} tokens", userId, tokens);
        } catch (Exception e) {
            // 配额记录失败不影响主流程（LLM 已成功调用）
            log.warn("记录配额使用失败 userId={}, tokens={}: {}", userId, tokens, e.getMessage());
        }
    }

    /**
     * 查询用户配额概览（供 QuotaController 展示）。
     */
    @Transactional(readOnly = true)
    public Optional<UserQuota> getQuota(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return quotaRepository.findByUserId(userId);
    }

    /**
     * 升级用户为 PREMIUM 等级（提升配额上限）。
     */
    @Transactional
    public UserQuota upgradeToPremium(Long userId) {
        UserQuota quota = getOrCreateQuota(userId);
        quota.setTier(Tier.PREMIUM);
        quota.setDailyTokenLimit(premiumDailyTokenLimit);
        return quotaRepository.save(quota);
    }

    /**
     * 重置用户当日配额（管理员/测试用）。
     */
    @Transactional
    public UserQuota resetQuota(Long userId) {
        UserQuota quota = getOrCreateQuota(userId);
        quota.setUsedTokens(0L);
        quota.setResetDate(LocalDate.now());
        return quotaRepository.save(quota);
    }

    // ============================================================
    // 内部辅助方法
    // ============================================================

    /** 查询或创建用户配额记录（首次访问时按 DEFAULT 等级初始化） */
    private UserQuota getOrCreateQuota(Long userId) {
        return quotaRepository.findByUserId(userId)
                .orElseGet(() -> {
                    UserQuota created = UserQuota.builder()
                            .userId(userId)
                            .tier(Tier.DEFAULT)
                            .dailyTokenLimit(defaultDailyTokenLimit)
                            .usedTokens(0L)
                            .resetDate(LocalDate.now())
                            .build();
                    log.info("初始化用户配额记录: userId={}, limit={}", userId, defaultDailyTokenLimit);
                    return quotaRepository.save(created);
                });
    }

    /** 跨日重置：若配额的 resetDate 早于今天，重置 usedTokens=0 并更新 resetDate */
    private UserQuota resetIfStale(UserQuota quota) {
        LocalDate today = LocalDate.now();
        if (quota.getResetDate() == null || quota.getResetDate().isBefore(today)) {
            quota.setUsedTokens(0L);
            quota.setResetDate(today);
            return quotaRepository.save(quota);
        }
        return quota;
    }
}
