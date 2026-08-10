package com.focusos.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sprint 8-E: Prometheus 指标配置 (Task 11)
 * <p>
 * 自定义业务指标 Bean，配合 {@code application.yml} 中的 {@code management.endpoints.web.exposure.include=prometheus}
 * 暴露 {@code /actuator/prometheus} 端点供 Prometheus 抓取，Grafana 通过 PromQL 查询展示。
 * <p>
 * 核心指标：
 * <ul>
 *   <li>{@code workflow_success_total} — Workflow 成功计数（Counter，tag: type）</li>
 *   <li>{@code workflow_failed_total}  — Workflow 失败计数（Counter，tag: type, reason）</li>
 *   <li>{@code workflow_duration_seconds} — Workflow 执行耗时分布（Timer，tag: type）</li>
 *   <li>{@code llm_token_usage}        — LLM Token 消耗计数（Counter，tag: agent, direction=in/out）</li>
 *   <li>{@code agent_score}            — Agent 评估得分（Gauge / Counter，tag: agent）</li>
 * </ul>
 * <p>
 * 这些指标 Bean 由 {@link com.focusos.service.WorkflowScheduler} /
 * {@link com.focusos.service.LLMGateway} 等业务组件注入并调用，提供运行时可观测数据。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MetricsConfig {

    private final MeterRegistry meterRegistry;

    /** Workflow 成功计数（按类型聚合：CAREER_ANALYSIS / LEARNING_PLAN / INTERVIEW...） */
    @Bean
    public Counter workflowSuccessCounter() {
        return Counter.builder("workflow_success_total")
                .description("Workflow 执行成功总数")
                .tags(Tags.of("application", "focusos-ai"))
                .register(meterRegistry);
    }

    /** Workflow 失败计数（按类型聚合） */
    @Bean
    public Counter workflowFailedCounter() {
        return Counter.builder("workflow_failed_total")
                .description("Workflow 执行失败总数")
                .tags(Tags.of("application", "focusos-ai"))
                .register(meterRegistry);
    }

    /** Workflow 执行耗时分布（Prometheus histogram） */
    @Bean
    public Timer workflowDurationTimer() {
        return Timer.builder("workflow_duration_seconds")
                .description("Workflow 执行耗时分布")
                .tags(Tags.of("application", "focusos-ai"))
                .publishPercentiles(0.5, 0.9, 0.99)
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    /** LLM Token 消耗计数（按 Agent 类型与方向 in/out 聚合） */
    @Bean
    public Counter llmTokenUsageCounter() {
        return Counter.builder("llm_token_usage")
                .description("LLM Token 消耗总数")
                .tags(Tags.of("application", "focusos-ai"))
                .register(meterRegistry);
    }

    /** Agent 评估得分累加器（用于按 Agent 类型聚合评估质量） */
    @Bean
    public Counter agentScoreCounter() {
        return Counter.builder("agent_score")
                .description("Agent 评估得分累计（用于计算平均分）")
                .tags(Tags.of("application", "focusos-ai"))
                .register(meterRegistry);
    }

    /**
     * 启动时记录一次自定义指标初始化，便于验证 Prometheus 端点能正确采集。
     */
    @Bean
    public String metricsInitializedLog() {
        log.info("Sprint 8-E: 业务指标 Bean 已注册（workflow_success_total / workflow_failed_total / "
                + "workflow_duration_seconds / llm_token_usage / agent_score），Prometheus 端点 /actuator/prometheus");
        return "metrics-initialized";
    }
}
