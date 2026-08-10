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
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sprint 8-D: RAG Evaluator — RAG 检索质量评估器
 * <p>
 * 职责：使用 LLM 对 RAG 流水线（检索 + 生成）进行端到端质量评估，
 * 输出 3 个核心指标：
 * <p>
 * - contextRecall    上下文召回率（0-100）：检索到的上下文是否包含回答问题所需的信息
 * - contextPrecision 上下文精确率（0-100）：检索到的上下文是否相关，有无过多无关内容
 * - faithfulness      忠实度（0-100）：生成的回答是否基于检索上下文，无幻觉
 * <p>
 * 综合分 overallScore = (contextRecall + contextPrecision + faithfulness) / 3.0
 * <p>
 * 适用场景：
 * - RAGAgent 输出后做端到端评估
 * - 评估不同 embedding 模型 / chunk 策略 / minScore 阈值对检索质量的影响
 * - 监控 RAG 系统在线质量漂移
 * <p>
 * 设计原则：
 * - 若 retrievedContext 为空：所有指标归零，issues=["检索上下文为空"]
 * - 若 answer 为空：所有指标归零，issues=["回答为空"]
 * - LLM 调用异常时：所有指标归零，issues=["评估异常: ..."]
 * - 所有异常被捕获，不会向上抛出
 * <p>
 * 技术实现（复用 Sprint 7-C-A 稳定化方案）：
 * - LLMJsonSanitizer 多层清洗保证 JSON 100% 合法
 * - LLMCallContext (ThreadLocal) + LLMLoggingService 记录调用日志，agentType="rag_evaluator"
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RAGEvaluator {

    private final ChatLanguageModel chatLanguageModel;
    private final AgentPromptProvider promptProvider;
    private final LLMJsonSanitizer jsonSanitizer;
    private final ObjectMapper objectMapper;

    /**
     * 评估 RAG 流水线质量（端到端：检索 + 生成）
     *
     * @param question         用户原始问题
     * @param retrievedContext RAG 检索到的上下文（已拼接的文档片段）
     * @param answer           RAGAgent 生成的回答
     * @param userId           用户 ID（用于 observability 记录）
     * @return RAGMetrics，包含 contextRecall/contextPrecision/faithfulness/overallScore/issues
     */
    public RAGMetrics evaluate(String question, String retrievedContext, String answer, Long userId) {
        // 前置校验 1：检索上下文为空
        if (retrievedContext == null || retrievedContext.isBlank()) {
            log.warn("RAGEvaluator: retrievedContext empty for userId={}", userId);
            return zeroMetrics(List.of("检索上下文为空"));
        }
        // 前置校验 2：回答为空
        if (answer == null || answer.isBlank()) {
            log.warn("RAGEvaluator: answer empty for userId={}", userId);
            return zeroMetrics(List.of("回答为空"));
        }

        String prompt = String.format("""
                请评估以下 RAG（检索增强生成）流水线的质量。

                【用户原始问题】
                %s

                【检索到的上下文】
                %s

                【RAG 生成的回答】
                %s

                【评估要求 — 必须严格遵守】
                1. 评估 3 个指标（均为 0-100 整数）：
                   - contextRecall（上下文召回率）：检索到的上下文是否包含回答问题所需的信息？
                     * 检索到了所有关键信息 → 90-100
                     * 检索到大部分关键信息 → 70-89
                     * 检索到部分关键信息 → 40-69
                     * 检索到的关键信息很少 → 0-39
                   - contextPrecision（上下文精确率）：检索到的上下文是否相关，有无过多无关内容？
                     * 全部相关，无冗余 → 90-100
                     * 大部分相关，少量冗余 → 70-89
                     * 部分相关，较多冗余 → 40-69
                     * 大部分无关 → 0-39
                   - faithfulness（忠实度）：回答是否基于检索上下文，无幻觉？
                     * 完全基于上下文，无幻觉 → 90-100
                     * 基本基于上下文，少量推断 → 70-89
                     * 部分脱离上下文，存在推断 → 40-69
                     * 严重脱离上下文，大量幻觉 → 0-39
                2. issues 列表中每个问题必须具体指出哪里不足（如"上下文缺少 XX 关键信息""回答中提到 XX 但上下文未提供"）。
                3. 输出必须是严格的 JSON 格式，不要输出 Markdown 代码块包裹。
                4. JSON 字符串值内部禁止使用未转义的双引号，如需引用文本请使用单引号（'）。

                【输出 JSON 格式】
                {"contextRecall": 85, "contextPrecision": 80, "faithfulness": 90, "issues": ["问题描述1", "问题描述2"]}
                """, truncate(question, 2000), truncate(retrievedContext, 5000), truncate(answer, 5000));

        String fullPrompt = promptProvider.evaluationSystemPrompt() + "\n\n" + prompt;

        LLMCallContext.set(userId, null, "rag_evaluator");
        try {
            String response = chatLanguageModel.chat(fullPrompt);
            return parseRagMetrics(response);
        } catch (Exception e) {
            log.warn("RAGEvaluator LLM call failed for userId={}, falling back to heuristic evaluation: {}",
                    userId, e.getMessage());
            return heuristicEvaluate(question, retrievedContext, answer);
        } finally {
            LLMCallContext.clear();
        }
    }

    // ============================================================
    // 启发式降级评估（LLM 不可用时使用）
    // ============================================================

    /**
     * 启发式评估 RAG 质量（LLM 降级路径）
     * <p>
     * 当 LLM 服务不可用（如账户欠费、网络异常）时，使用基于关键词匹配的
     * 启发式方法计算 3 个核心指标，确保系统在 LLM 不可用时仍能提供基础评估能力。
     * <p>
     * 算法：
     * - contextRecall: answer 中关键词在 retrievedContext 出现的比例
     * - contextPrecision: retrievedContext 中句子与 question 的相关度
     * - faithfulness: answer 中每个句子是否能从 retrievedContext 找到依据
     *
     * @param question         用户原始问题
     * @param retrievedContext RAG 检索到的上下文
     * @param answer           RAG 生成的回答
     * @return RAGMetrics 启发式评估结果
     */
    private RAGMetrics heuristicEvaluate(String question, String retrievedContext, String answer) {
        List<String> issues = new ArrayList<>();

        // 提取关键词
        Set<String> questionKeys = extractKeywords(question);
        Set<String> contextKeys = extractKeywords(retrievedContext);
        Set<String> answerKeys = extractKeywords(answer);

        // 1. contextRecall: answer 关键词在 context 中的覆盖率
        int contextRecall;
        if (answerKeys.isEmpty()) {
            contextRecall = 50;
        } else {
            long matched = answerKeys.stream().filter(contextKeys::contains).count();
            double ratio = (double) matched / answerKeys.size();
            // 完全覆盖给 85-95，大部分覆盖给 60-84，部分覆盖给 30-59
            if (ratio >= 0.9) contextRecall = 90;
            else if (ratio >= 0.7) contextRecall = 75;
            else if (ratio >= 0.5) contextRecall = 60;
            else if (ratio >= 0.3) contextRecall = 40;
            else contextRecall = 20;
            if (ratio < 1.0) {
                issues.add("回答中部分关键词未在检索上下文中找到（启发式评估）");
            }
        }

        // 2. contextPrecision: context 中与 question 相关的内容比例
        int contextPrecision;
        if (contextKeys.isEmpty()) {
            contextPrecision = 50;
        } else {
            // 检查 context 中有多少句子包含 question 关键词
            List<String> contextSentences = splitSentences(retrievedContext);
            if (contextSentences.isEmpty()) {
                contextPrecision = 50;
            } else {
                long relevantSentences = contextSentences.stream()
                        .filter(s -> {
                            Set<String> sentKeys = extractKeywords(s);
                            return sentKeys.stream().anyMatch(questionKeys::contains);
                        })
                        .count();
                double relRatio = (double) relevantSentences / contextSentences.size();
                if (relRatio >= 0.8) contextPrecision = 90;
                else if (relRatio >= 0.6) contextPrecision = 75;
                else if (relRatio >= 0.4) contextPrecision = 60;
                else if (relRatio >= 0.2) contextPrecision = 40;
                else contextPrecision = 30;
                if (relRatio < 0.5) {
                    issues.add("检索上下文包含较多无关内容（启发式评估）");
                }
            }
        }

        // 3. faithfulness: answer 中每个句子是否都能从 context 找到依据
        int faithfulness;
        List<String> answerSentences = splitSentences(answer);
        if (answerSentences.isEmpty()) {
            faithfulness = 50;
        } else {
            long supported = answerSentences.stream()
                    .filter(s -> {
                        Set<String> sentKeys = extractKeywords(s);
                        if (sentKeys.isEmpty()) return true;
                        return sentKeys.stream().anyMatch(contextKeys::contains);
                    })
                    .count();
            double supRatio = (double) supported / answerSentences.size();
            if (supRatio >= 0.95) faithfulness = 92;
            else if (supRatio >= 0.8) faithfulness = 80;
            else if (supRatio >= 0.6) faithfulness = 60;
            else if (supRatio >= 0.4) faithfulness = 40;
            else faithfulness = 20;
            if (supRatio < 1.0) {
                issues.add("回答中部分内容未在检索上下文中找到依据，可能存在幻觉（启发式评估）");
            }
        }

        double overall = (contextRecall + contextPrecision + faithfulness) / 3.0;
        log.info("RAGEvaluator heuristic result: recall={}, precision={}, faithfulness={}, overall={}",
                contextRecall, contextPrecision, faithfulness, overall);

        return RAGMetrics.builder()
                .contextRecall(contextRecall)
                .contextPrecision(contextPrecision)
                .faithfulness(faithfulness)
                .overallScore(overall)
                .issues(issues)
                .build();
    }

    /**
     * 提取文本中的关键词（简易分词）
     * 策略：移除停用词，提取 2 字以上中文词组或英文单词
     */
    private static Set<String> extractKeywords(String text) {
        Set<String> keywords = new java.util.HashSet<>();
        if (text == null || text.isBlank()) return keywords;

        // 英文单词（2 字以上）
        java.util.regex.Matcher enMatcher = ENGLISH_WORD_PATTERN.matcher(text);
        while (enMatcher.find()) {
            String word = enMatcher.group().toLowerCase();
            if (!STOP_WORDS.contains(word) && word.length() >= 2) {
                keywords.add(word);
            }
        }

        // 中文关键词：按停用词和标点分割，提取 2 字以上片段
        String cleaned = CN_PUNCTUATION_PATTERN.matcher(text).replaceAll(" ");
        for (String stop : CN_STOP_WORDS) {
            cleaned = cleaned.replace(stop, " ");
        }
        for (String token : cleaned.split("\\s+")) {
            if (token.length() >= 2 && !STOP_WORDS.contains(token.toLowerCase())) {
                keywords.add(token);
            }
        }
        return keywords;
    }

    /**
     * 按句号、问号、感叹号、分号、逗号分割句子（细粒度，便于检测部分幻觉）
     */
    private static List<String> splitSentences(String text) {
        List<String> sentences = new ArrayList<>();
        if (text == null || text.isBlank()) return sentences;
        String[] parts = text.split("[。！？；，,!?,;\\n]");
        for (String p : parts) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty() && trimmed.length() >= 2) {
                sentences.add(trimmed);
            }
        }
        return sentences;
    }

    private static final java.util.regex.Pattern ENGLISH_WORD_PATTERN =
            java.util.regex.Pattern.compile("[a-zA-Z]{2,}");
    private static final java.util.regex.Pattern CN_PUNCTUATION_PATTERN =
            java.util.regex.Pattern.compile("[\uff0c\u3001,.()\uff08\uff09\\[\\]\u3010\u3011\\u201c\\u201d\\u2018\\u2019\uff1a:{}]+");
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "are", "was", "were", "has", "have", "had",
            "this", "that", "with", "from", "not", "but", "they", "their",
            "will", "would", "can", "could", "should", "shall", "may", "might",
            "you", "your", "our", "its", "been", "being", "into", "than"
    );
    private static final String[] CN_STOP_WORDS = {
            "我们", "你们", "他们", "这个", "那个", "这些", "那些", "什么", "怎么",
            "可以", "能够", "应该", "需要", "已经", "进行", "通过", "使用", "采用",
            "一个", "一些", "如果", "因为", "所以", "但是", "而且", "或者", "以及",
            "对于", "关于", "根据", "作为", "属于", "使得", "从而", "以及", "等等",
            "用户", "系统", "项目", "使用", "进行", "实现", "包括", "包含", "基于"
    };

    // ============================================================
    // 解析与规范化
    // ============================================================

    /**
     * 解析 LLM 输出为 RAGMetrics
     * <p>
     * 流程：
     * 1. LLMJsonSanitizer 清洗原始输出
     * 2. ObjectMapper 解析为 RAGMetrics
     * 3. 若解析失败：降级为 Map 解析
     * 4. 若彻底解析失败：返回全零结果 + 异常 issue
     */
    private RAGMetrics parseRagMetrics(String llmOutput) {
        if (llmOutput == null || llmOutput.isBlank()) {
            return zeroMetrics(List.of("LLM 输出为空"));
        }

        // 路径 1：直接解析为 RAGMetrics
        String clean = jsonSanitizer.sanitize(llmOutput);
        try {
            RAGMetrics direct = objectMapper.readValue(clean, RAGMetrics.class);
            if (direct != null && direct.getContextRecall() != null) {
                normalizeResult(direct);
                return direct;
            }
        } catch (Exception e) {
            log.debug("RAGMetrics direct parse failed: {}", e.getMessage());
        }

        // 路径 2：降级为 Map 手工提取
        try {
            Map<String, Object> map = objectMapper.readValue(clean, new TypeReference<Map<String, Object>>() {});
            RAGMetrics fromMap = buildFromMap(map);
            if (fromMap != null) {
                normalizeResult(fromMap);
                return fromMap;
            }
        } catch (Exception e) {
            log.warn("RAGMetrics parse failed completely, raw len={}: {}", clean.length(),
                    clean.substring(0, Math.min(200, clean.length())));
        }
        return zeroMetrics(List.of("评估异常: LLM 输出无法解析为 RAGMetrics"));
    }

    /**
     * 从 Map 构建 RAGMetrics（降级解析路径）
     */
    private RAGMetrics buildFromMap(Map<String, Object> map) {
        Integer contextRecall = getAsInt(map, "contextRecall");
        if (contextRecall == null) return null;

        Integer contextPrecision = getAsInt(map, "contextPrecision");
        Integer faithfulness = getAsInt(map, "faithfulness");
        List<String> issues = toStringList(map.get("issues"));

        return RAGMetrics.builder()
                .contextRecall(contextRecall)
                .contextPrecision(contextPrecision)
                .faithfulness(faithfulness)
                .issues(issues)
                .build();
    }

    /**
     * 规范化 RAGMetrics：
     * - 钳制 3 个指标到 0-100
     * - 若指标为 null，默认为 0
     * - 计算 overallScore = (contextRecall + contextPrecision + faithfulness) / 3.0
     * - issues 为 null 时初始化为空列表
     */
    private void normalizeResult(RAGMetrics result) {
        result.setContextRecall(clamp(result.getContextRecall()));
        result.setContextPrecision(clamp(result.getContextPrecision()));
        result.setFaithfulness(clamp(result.getFaithfulness()));

        // 计算 overallScore
        double overall = (result.getContextRecall() + result.getContextPrecision() + result.getFaithfulness()) / 3.0;
        result.setOverallScore(overall);

        if (result.getIssues() == null) {
            result.setIssues(new ArrayList<>());
        }
    }

    /**
     * 构造全零指标结果（用于前置校验失败或异常场景）
     */
    private RAGMetrics zeroMetrics(List<String> issues) {
        return RAGMetrics.builder()
                .contextRecall(0)
                .contextPrecision(0)
                .faithfulness(0)
                .overallScore(0.0)
                .issues(issues != null ? issues : new ArrayList<>())
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
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
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
    // DTO: RAG 评估指标
    // ============================================================

    /**
     * RAG 流水线质量评估指标 DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RAGMetrics {
        /**
         * 上下文召回率（0-100）：检索到的上下文是否包含回答问题所需的信息
         */
        private Integer contextRecall;
        /**
         * 上下文精确率（0-100）：检索到的上下文是否相关，有无过多无关内容
         */
        private Integer contextPrecision;
        /**
         * 忠实度（0-100）：回答是否基于检索上下文，无幻觉
         */
        private Integer faithfulness;
        /**
         * 综合得分 = (contextRecall + contextPrecision + faithfulness) / 3.0
         */
        private Double overallScore;
        /**
         * 发现的具体问题列表
         */
        private List<String> issues;
    }
}
