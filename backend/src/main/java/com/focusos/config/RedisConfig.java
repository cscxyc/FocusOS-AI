package com.focusos.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Sprint 8-E: Redis 配置（手动装配 RedisTemplate）
 * <p>
 * 背景：{@code application.yml} 已排除 {@code RedisAutoConfiguration}，因此 Spring Boot 不会自动创建
 * {@link RedisTemplate}。这里手动定义 {@link RedisTemplate} Bean，使用 Jackson 序列化 value，
 * 以便 {@link com.focusos.service.CacheService} 缓存任意对象（Career Analysis / Resume Evaluation 结果等）。
 * <p>
 * 装配条件（两层保护，确保 Redis 真正可用才创建 Bean，否则 {@link com.focusos.service.CacheService} 降级到本地内存）：
 * <ol>
 *   <li>类级 {@code @ConditionalOnProperty(focusos.cache.enabled=true)}（默认开启）</li>
 *   <li>Bean 级 {@code @ConditionalOnBean(RedisConnectionFactory.class)} — 仅当容器中存在
 *       {@link RedisConnectionFactory} 时才创建（Redis 连接由外部提供；若未启动 Redis 则不创建）</li>
 * </ol>
 * <p>
 * 当 {@link RedisConnectionFactory} 不存在时，本 Bean 不创建，
 * {@link com.focusos.service.CacheService} 通过 {@code @Autowired(required = false)} 拿到 {@code null}，
 * 自动降级到 {@code ConcurrentHashMap} 本地内存缓存。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "focusos.cache.enabled", havingValue = "true", matchIfMissing = true)
public class RedisConfig {

    /**
     * 配置 {@link RedisTemplate}：
     * <ul>
     *   <li>key / hashKey：{@link StringRedisSerializer}（键为可读字符串，便于排查）</li>
     *   <li>value / hashValue：{@link GenericJackson2JsonRedisSerializer}（JSON + 默认类型信息，
     *       反序列化时还原为原始类型，配合 {@code CacheService.get(key, Class)} 的类型转换）</li>
     * </ul>
     * 自定义 ObjectMapper 注册了 {@link JavaTimeModule}，以支持实体中的 {@code LocalDateTime} 等日期类型。
     */
    @Bean
    @ConditionalOnBean(RedisConnectionFactory.class)
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(buildObjectMapper());

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();

        log.info("RedisTemplate<String, Object> 已装配（Jackson 序列化），CacheService 将使用 Redis");
        return template;
    }

    /**
     * 构建带默认类型信息 + JavaTime 支持的 ObjectMapper，供 Redis JSON 序列化使用。
     * <p>
     * {@code activateDefaultTyping(NON_FINAL)} 会在 JSON 中写入 {@code @class} 字段，
     * 使反序列化能还原为原始类型，避免返回 {@code LinkedHashMap}。
     */
    private ObjectMapper buildObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        return mapper;
    }
}
