package com.focusos.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sprint 8-D: Evaluation Agent — Agent 输出质量评估专家
 * <p>
 * 职责：对其他 Agent（CareerAgent / ResumeOptimizationAgent / InterviewAgent 等）的输出
 * 进行多维度质量评估，输出结构化评分和改进建议。
 * <p>
 * 评分体系（总分 0-100）：
 * - accuracy       准确度：是否准确响应了输入需求，有无错误信息
 * - completeness   完整度：是否覆盖了输入要求的所有关键点
 * - grounding      事实依据：事实性陈述是否有用户事实依据，无依据断言扣分
 * - actionability  可执行性：输出是否具体可执行，而非泛泛而谈
 * <p>
 * 质量控制：
 * - 4 维度评分，综合分 = (accuracy + completeness + grounding + actionability) / 4
 * - 包含幻觉检测：明显编造的事实会导致 grounding <= 40
 * - 支持解析失败重试：首次解析失败追加格式提醒后重试一次
 * - 全部异常被捕获，不会向上抛出
 * <p>
 * 技术实现（复用 Sprint 7-C-A 稳定化方案）：
 * - LLMJsonSanitizer 多层清洗保证 JSON 100% 合法
 * - LLMCallContext (ThreadLocal) + LLMLoggingService 记录调用日志，agentType="evaluation"
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EvaluationAgent implements FocusAgent {

    private final ChatLanguageModel chatLanguageModel;
    private final AgentPromptProvider promptProvider;
    private final LLMJsonSanitizer jsonSanitizer;
    private final ObjectMapper objectMapper;

    /**
     * 默认评估类型：综合评估（accuracy/completeness/grounding/actionability 四维度）
     */
    private static final String DEFAULT_EVALUATION_TYPE = "comprehensive";

    @Override
    public String type() {
        return "evaluation";
    }

    /**
     * 实现 FocusAgent 接口，走通用路由（简单包装主方法）
     * <p>
     * message 期望为 JSON：{"agentType":"...", "input":"...", "output":"..."}
     */
    @Override
    public String handle(String message, Long userId, String context) {
        try {
            String agentType = "unknown";
            String input = "";
            String output = "";
            try {
                Map<String, Object> m = objectMapper.readValue(message, new TypeReference<Map<String, Object>>() {});
                if (m.get("agentType") != null) agentType = m.get("agentType").toString();
                if (m.get("input") != null) input = m.get("input").toString();
                if (m.get("output") != null) output = m.get("output").toString();
            } catch (Exception ignore) {
                // 非 JSON，把 message 当 output
                output = message;
            }
            EvaluationResult result = evaluate(agentType, input, output, userId);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("EvaluationAgent handle failed for userId={}", userId, e);
            return jsonSanitizer.serialize(EvaluationResult.builder()
                    .score(0)
                    .metrics(new LinkedHashMap<>())
                    .issues(List.of("评估失败: " + e.getMessage()))
                    .feedback("评估失败")
                    .build());
        }
    }

    // ============================================================
    // 核心业务方法
    // ============================================================

    /**
     * 对指定 Agent 的输出进行综合质量评估（默认 comprehensive 类型）
     *
     * @param agentType 被评估的 Agent 类型标识（career/resume_optimization/interview/memory 等）
     * @param input     Agent 的原始输入
     * @param output    Agent 的实际输出
     * @param userId    用户 ID（用于 observability 记录）
     * @return 结构化评估结果（EvaluationResult）
     */
    public EvaluationResult evaluate(String agentType, String input, String output, Long userId) {
        return evaluate(agentType, DEFAULT_EVALUATION_TYPE, input, output, userId);
    }

    /**
     * 对指定 Agent 的输出进行指定类型的质量评估
     *
     * @param agentType       被评估的 Agent 类型标识（career/resume_optimization/interview/memory 等）
     * @param evaluationType 评估类型（comprehensive/accuracy_only/grounding_focus 等）
     * @param input          Agent 的原始输入
     * @param output         Agent 的实际输出
     * @param userId         用户 ID（用于 observability 记录）
     * @return 结构化评估结果（EvaluationResult）
     */
    public EvaluationResult evaluate(String agentType, String evaluationType, String input, String output, Long userId) {
        String type = evaluationType == null || evaluationType.isBlank() ? DEFAULT_EVALUATION_TYPE : evaluationType;
        String agent = agentType == null || agentType.isBlank() ? "unknown" : agentType;

        String prompt = String.format("""
                请对以下 AI Agent 的输出质量进行严格评估。

                【评估类型】
                %s

                【被评估 Agent 类型】
                %s

                【Agent 原始输入】
                %s

                【Agent 实际输出】
                %s

                【评估要求 — 必须严格遵守】
                1. 评分基于 4 个维度：accuracy（准确度）、completeness（完整度）、grounding（事实依据）、actionability（可执行性）。
                2. 每个维度 0-100 整数分，综合 score = (accuracy + completeness + grounding + actionability) / 4。
                3. accuracy：Agent 输出是否准确响应了输入需求，有无错误信息。
                4. completeness：输出是否覆盖了输入要求的所有关键点，有无遗漏。
                5. grounding：输出中的事实性陈述是否有用户事实依据，无依据的断言扣分；明显幻觉（编造不存在的事实）时 grounding <= 40。
                6. actionability：输出是否具体可执行，而非泛泛而谈。
                7. issues 列表中每个问题必须具体指出哪里不足，不可笼统说「不够好」。
                8. feedback 字段给出 1-3 条具体改进建议。
                9. 输出必须是严格的 JSON 格式，不要输出 Markdown 代码块包裹。
                10. JSON 字符串值内部禁止使用未转义的双引号，如需引用文本请使用单引号（'）。

                【输出 JSON 格式】
                {"score": 85, "metrics": {"accuracy": 90, "completeness": 80, "grounding": 90, "actionability": 85}, "issues": ["问题描述1", "问题描述2"], "feedback": "改进建议"}
                """, type, agent, truncate(input, 5000), truncate(output, 5000));

        String fullPrompt = promptProvider.evaluationSystemPrompt() + "\n\n" + prompt;

        LLMCallContext.set(userId, null, "evaluation");
        try {
            String response = chatLanguageModel.chat(fullPrompt);
            EvaluationResult result = parseEvaluationResult(response);

            // 首次解析失败重试（追加 JSON 格式提醒）
            if (result == null || result.getScore() == null) {
                log.warn("First evaluation parse failed for agentType={}, retrying with JSON format reminder...", agent);
                String retryPrompt = fullPrompt + "\n\n【重要提醒】你上次的输出不符合 JSON 格式。请严格按如下 JSON 格式重新输出，绝对不要输出 ```json 代码块包裹，字符串内部不要使用双引号，用单引号替代：\n{\"score\": 85, \"metrics\": {\"accuracy\": 90, \"completeness\": 80, \"grounding\": 90, \"actionability\": 85}, \"issues\": [\"问题描述\"], \"feedback\": \"改进建议\"}";
                String retryResponse = chatLanguageModel.chat(retryPrompt);
                EvaluationResult retry = parseEvaluationResult(retryResponse);
                if (retry != null && retry.getScore() != null) {
                    result = retry;
                }
            }

            if (result == null || result.getScore() == null) {
                return defaultFailedResult("评估解析失败：LLM 输出无法识别");
            }
            normalizeResult(result);
            return result;
        } catch (Exception e) {
            log.error("EvaluationAgent evaluate LLM call failed for userId={} agentType={}", userId, agent, e);
            return defaultFailedResult("评估失败: " + e.getMessage());
        } finally {
            LLMCallContext.clear();
        }
    }

    // ============================================================
    // 解析与规范化
    // ============================================================

    /**
     * 解析 LLM 评估输出为 EvaluationResult DTO
     * <p>
     * 流程：
     * 1. LLMJsonSanitizer 清洗原始输出
     * 2. ObjectMapper 解析为 EvaluationResult
     * 3. 若解析失败：降级为 Map 解析
     */
    private EvaluationResult parseEvaluationResult(String llmOutput) {
        if (llmOutput == null || llmOutput.isBlank()) return null;

        // 路径 1：直接解析为 EvaluationResult DTO
        String clean = jsonSanitizer.sanitize(llmOutput);
        try {
            EvaluationResult direct = objectMapper.readValue(clean, EvaluationResult.class);
            if (direct != null && direct.getScore() != null) {
                return direct;
            }
        } catch (Exception e) {
            log.debug("EvaluationResult direct parse failed: {}", e.getMessage());
        }

        // 路径 2：降级为 Map 手工提取
        try {
            Map<String, Object> map = objectMapper.readValue(clean, new TypeReference<Map<String, Object>>() {});
            return buildFromMap(map);
        } catch (Exception e) {
            log.warn("EvaluationResult parse failed completely, raw len={}: {}", clean.length(),
                    clean.substring(0, Math.min(200, clean.length())));
            return null;
        }
    }

    /**
     * 从 Map 构建 EvaluationResult（降级解析路径）
     */
    private EvaluationResult buildFromMap(Map<String, Object> map) {
        Integer score = getAsInt(map, "score");
        if (score == null) return null;

        Map<String, Integer> metrics = new LinkedHashMap<>();
        Object metricsObj = map.get("metrics");
        if (metricsObj instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> entry : m.entrySet()) {
                if (entry.getKey() == null) continue;
                Integer v = toInt(entry.getValue());
                if (v != null) {
                    metrics.put(entry.getKey().toString(), v);
                }
            }
        }

        List<String> issues = toStringList(map.get("issues"));
        String feedback = getAsString(map, "feedback");

        return EvaluationResult.builder()
                .score(score)
                .metrics(metrics)
                .issues(issues)
                .feedback(feedback)
                .build();
    }

    /**
     * 规范化 EvaluationResult：
     * - clamp score 0-100
     * - clamp 每个 metric 0-100
     * - 若 metrics 为空但 score 存在，创建默认 metrics（四维度均等于 score）
     */
    private void normalizeResult(EvaluationResult result) {
        // 钳制 score
        result.setScore(clamp(result.getScore()));

        // 钳制每个 metric
        Map<String, Integer> metrics = result.getMetrics();
        if (metrics == null) {
            metrics = new LinkedHashMap<>();
        } else {
            Map<String, Integer> clamped = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> entry : metrics.entrySet()) {
                clamped.put(entry.getKey(), clamp(entry.getValue()));
            }
            metrics = clamped;
        }

        // 若 metrics 为空但 score 存在，创建默认 metrics（四维度均等于 score）
        if (metrics.isEmpty() && result.getScore() != null) {
            metrics.put("accuracy", result.getScore());
            metrics.put("completeness", result.getScore());
            metrics.put("grounding", result.getScore());
            metrics.put("actionability", result.getScore());
        }
        result.setMetrics(metrics);

        if (result.getIssues() == null) {
            result.setIssues(new ArrayList<>());
        }
        if (result.getFeedback() == null) {
            result.setFeedback("");
        }
    }

    /**
     * 构造失败时的默认 EvaluationResult
     */
    private EvaluationResult defaultFailedResult(String feedback) {
        return EvaluationResult.builder()
                .score(0)
                .metrics(new LinkedHashMap<>())
                .issues(new ArrayList<>())
                .feedback(feedback)
                .build();
    }

    // ============================================================
    // 工具方法
    // ============================================================

    private static Integer clamp(Integer v) {
        if (v == null) return 0;
        if (v < 0) return 0;
        if (v > 100) return 100;
        return v;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max) + "...（truncated " + (s.length() - max) + " chars）";
    }

    private static Integer getAsInt(Map<?, ?> map, String key) {
        Object v = map.get(key);
        return toInt(v);
    }

    private static Integer toInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String getAsString(Map<?, ?> map, String key) {
        Object v = map.get(key);
        return v == null ? null : v.toString();
    }

    private static List<String> toStringList(Object obj) {
        List<String> result = new ArrayList<>();
        if (obj instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) result.add(o.toString());
            }
        }
        return result;
    }

    // ============================================================
    // DTO: 评估结果
    // ============================================================

    /**
     * Agent 输出评估结果 DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvaluationResult {
        /**
         * 综合评分 0-100
         */
        private Integer score;
        /**
         * 多维度评分：accuracy / completeness / grounding / actionability
         */
        private Map<String, Integer> metrics;
        /**
         * 发现的具体问题列表
         */
        private List<String> issues;
        /**
         * 改进建议（1-3 条）
         */
        private String feedback;
    }
}
