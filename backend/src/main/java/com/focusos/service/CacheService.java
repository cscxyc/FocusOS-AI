package com.focusos.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Sprint 8-E: 缓存服务（Redis 优先，降级到本地内存）
 * <p>
 * 职责：缓存 Career Analysis / Resume Evaluation 等 LLM 分析结果，减少重复 LLM 调用，降低成本与延迟。
 * <p>
 * 实现策略：
 * <ul>
 *   <li>Redis 可用（{@code redisTemplate != null} 且 {@code enabled=true}）→ 使用 Redis，带 TTL</li>
 *   <li>Redis 不可用 / 写入失败 → 降级到 {@link ConcurrentHashMap} 本地内存缓存（带 TTL 过期）</li>
 * </ul>
 * <p>
 * {@link RedisTemplate} 通过 {@code @Autowired(required = false)} 注入：当 {@link com.focusos.config.RedisConfig}
 * 未装配（无 {@code RedisConnectionFactory} 或 {@code cache.enabled=false}）时为 {@code null}，不影响启动。
 */
@Slf4j
@Service
public class CacheService {

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${focusos.cache.enabled:true}")
    private boolean enabled;

    @Value("${focusos.cache.ttl-minutes:30}")
    private int ttlMinutes;

    /** 本地内存缓存（Redis 不可用时降级使用），key → 带过期时间的缓存条目 */
    private final ConcurrentHashMap<String, CacheEntry> localCache = new ConcurrentHashMap<>();

    /**
     * 缓存功能是否可用。
     * <p>
     * 只要 {@code enabled=true} 即视为可用（Redis 不可用时自动降级到本地内存，仍可缓存）。
     */
    public boolean isAvailable() {
        return enabled;
    }

    /** Redis 是否真正可用（模板已注入且功能开启） */
    private boolean redisAvailable() {
        return enabled && redisTemplate != null;
    }

    /**
     * 获取缓存值并转换为指定类型。
     *
     * @param key  缓存键
     * @param type 期望的返回值类型
     * @param <T>  返回值类型
     * @return 命中则返回 {@link Optional#of(Object)}，未命中 / 类型不匹配 / 异常则返回 {@link Optional#empty()}
     */
    public <T> Optional<T> get(String key, Class<T> type) {
        if (!enabled) {
            return Optional.empty();
        }
        if (redisAvailable()) {
            try {
                Object raw = redisTemplate.opsForValue().get(key);
                return toTyped(raw, type);
            } catch (Exception e) {
                log.warn("Redis 读取失败 key={}: {}", key, e.getMessage());
                return Optional.empty();
            }
        }
        // 本地内存缓存
        CacheEntry entry = localCache.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.isExpired()) {
            localCache.remove(key, entry);
            return Optional.empty();
        }
        return toTyped(entry.value, type);
    }

    /**
     * 写入缓存（带 TTL）。
     * <p>
     * Redis 写入失败时自动降级到本地内存缓存，保证缓存一定写入。
     *
     * @param key   缓存键
     * @param value 缓存值（为 null 时跳过）
     */
    public void put(String key, Object value) {
        if (!enabled || value == null) {
            return;
        }
        if (redisAvailable()) {
            try {
                redisTemplate.opsForValue().set(key, value, Duration.ofMinutes(ttlMinutes));
                return;
            } catch (Exception e) {
                log.warn("Redis 写入失败 key={}, 降级到本地缓存: {}", key, e.getMessage());
            }
        }
        // 本地内存缓存（或 Redis 写入失败时降级）
        localCache.put(key, new CacheEntry(value, Duration.ofMinutes(ttlMinutes).toMillis()));
    }

    /**
     * 清除指定缓存键。
     *
     * @param key 缓存键
     */
    public void evict(String key) {
        localCache.remove(key);
        if (redisAvailable()) {
            try {
                redisTemplate.delete(key);
            } catch (Exception e) {
                log.warn("Redis 删除失败 key={}: {}", key, e.getMessage());
            }
        }
    }

    /**
     * 按 Redis glob 模式清除缓存（支持 {@code *} 通配符）。
     * <p>
     * 同时清除本地内存缓存与 Redis 中匹配的键。
     *
     * @param pattern glob 模式，如 {@code career_analysis:42:*}
     */
    public void evictPattern(String pattern) {
        // 本地缓存：将 glob 模式转为正则进行匹配
        Pattern regex = globToRegex(pattern);
        localCache.keySet().removeIf(k -> regex.matcher(k).matches());
        if (redisAvailable()) {
            try {
                Set<String> keys = redisTemplate.keys(pattern);
                if (keys != null && !keys.isEmpty()) {
                    redisTemplate.delete(keys);
                }
            } catch (Exception e) {
                log.warn("Redis 模式删除失败 pattern={}: {}", pattern, e.getMessage());
            }
        }
    }

    /**
     * 构建标准缓存键，格式：{@code prefix:userId:hash}
     *
     * @param prefix 业务前缀（如 {@code career_analysis}）
     * @param userId 用户 ID
     * @param hash   内容哈希（如 prompt 的 MD5）
     * @return 组合后的缓存键
     */
    public String buildKey(String prefix, Long userId, String hash) {
        return prefix + ":" + userId + ":" + hash;
    }

    /** 将原始值安全转换为期望类型；类型不匹配时返回 empty 并告警 */
    private <T> Optional<T> toTyped(Object raw, Class<T> type) {
        if (raw == null) {
            return Optional.empty();
        }
        if (type.isInstance(raw)) {
            return Optional.of(type.cast(raw));
        }
        log.warn("缓存类型不匹配，期望={} 实际={}", type.getSimpleName(), raw.getClass().getSimpleName());
        return Optional.empty();
    }

    /** 将 Redis glob 模式（{@code *} / {@code ?}）转换为 Java 正则表达式 */
    private static Pattern globToRegex(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return Pattern.compile(".*");
        }
        StringBuilder sb = new StringBuilder();
        for (char c : pattern.toCharArray()) {
            switch (c) {
                case '*':
                    sb.append(".*");
                    break;
                case '?':
                    sb.append('.');
                    break;
                case '.':
                    sb.append("\\.");
                    break;
                default:
                    if ("\\[](){}+^$|".indexOf(c) >= 0) {
                        sb.append('\\');
                    }
                    sb.append(c);
            }
        }
        return Pattern.compile(sb.toString());
    }

    /** 本地缓存条目（带过期时间，惰性过期） */
    private static class CacheEntry {
        final Object value;
        final long expireAt;

        CacheEntry(Object value, long ttlMillis) {
            this.value = value;
            this.expireAt = System.currentTimeMillis() + ttlMillis;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expireAt;
        }
    }
}
