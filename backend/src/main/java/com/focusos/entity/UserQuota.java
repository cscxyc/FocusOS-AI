package com.focusos.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Sprint 8-E: 用户配额实体 (Task 8)
 * <p>
 * 记录用户每日 Token 配额与使用情况，配合 {@link com.focusos.service.QuotaService}
 * 在 LLM 调用前进行配额检查（{@link com.focusos.service.LLMGateway#call} 调用前自动执行）。
 * <p>
 * 字段说明：
 * <ul>
 *   <li>{@code dailyTokenLimit}：每日 Token 上限（普通用户默认 10000，高级用户 100000）</li>
 *   <li>{@code usedTokens}：当日已使用 Token 数</li>
 *   <li>{@code resetDate}：配额重置日期（每日首次调用时若 {@code LocalDate.now() > resetDate}，重置 usedTokens=0）</li>
 *   <li>{@code tier}：用户等级（DEFAULT / PREMIUM），决定配额上限</li>
 * </ul>
 */
@Entity
@Table(name = "user_quotas", indexes = {
        @Index(name = "idx_quota_user", columnList = "userId"),
        @Index(name = "idx_quota_reset", columnList = "resetDate")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserQuota {

    /** 用户等级 */
    public enum Tier {
        /** 普通用户：默认 10000 tokens/day */
        DEFAULT,
        /** 高级用户：默认 100000 tokens/day */
        PREMIUM
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    /** 用户等级（决定 dailyTokenLimit 默认值） */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Tier tier;

    /** 每日 Token 上限 */
    @Column(nullable = false)
    private Long dailyTokenLimit;

    /** 当日已使用 Token 数 */
    @Column(nullable = false)
    private Long usedTokens;

    /** 配额重置日期（跨日后首次调用时重置 usedTokens） */
    @Column(nullable = false)
    private LocalDate resetDate;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
