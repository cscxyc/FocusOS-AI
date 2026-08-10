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
 * Sprint 8-D: Grounding Checker — 事实依据核查器
 * <p>
 * 职责：检查 Agent 输出（answer）中的每一条事实性陈述是否在用户的
 * Memory 上下文（memoryContext）或 RAG 检索上下文（ragContext）中有据可查。
 * <p>
 * 适用场景：
 * - 在 CareerAgent / InterviewAgent / RAGAgent 输出后做事实核查
 * - 检测幻觉：识别 answer 中编造的内容
 * - 生成 unsupportedClaims 列表，便于 EvaluationAgent 进一步扣分
 * <p>
 * 设计原则：
 * - 若 memoryContext 与 ragContext 同时为空：无法核查，返回 grounded=true + confidence=0.5（中性）
 * - LLM 调用异常时：返回 grounded=false，避免错误地"放行"含幻觉的输出
 * - 所有异常被捕获，不会向上抛出
 * <p>
 * 技术实现（复用 Sprint 7-C-A 稳定化方案）：
 * - LLMJsonSanitizer 多层清洗保证 JSON 100% 合法
 * - LLMCallContext (ThreadLocal) + LLMLoggingService 记录调用日志，agentType="grounding_check"
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroundingChecker {

    private final ChatLanguageModel chatLanguageModel;
    private final AgentPromptProvider promptProvider;
    private final LLMJsonSanitizer jsonSanitizer;
    private final ObjectMapper objectMapper;

    /**
     * 检查 answer 中每一条事实性陈述是否在 memoryContext 或 ragContext 中有据可查
     *
     * @param answer        待核查的 Agent 输出
     * @param memoryContext 用户长期记忆上下文（UserMemoryContext.renderAsPromptSection，可为空）
     * @param ragContext    RAG 检索上下文（Personal RAG 资料，可为空）
     * @param userId        用户 ID（用于 observability 记录）
     * @return GroundingResult，grounded=true 表示全部事实有据可查；false 表示存在无据断言
     */
    public GroundingResult check(String answer, String memoryContext, String ragContext, Long userId) {
        // 若 answer 为空：直接返回 grounded=true（无可核查内容）
        if (answer == null || answer.isBlank()) {
            return GroundingResult.builder()
                    .grounded(true)
                    .unsupportedClaims(new ArrayList<>())
                    .confidence(0.5)
                    .build();
        }

        // 若 memoryContext 与 ragContext 同时为空：无法核查，返回中性结果
        boolean memoryEmpty = memoryContext == null || memoryContext.isBlank();
        boolean ragEmpty = ragContext == null || ragContext.isBlank();
        if (memoryEmpty && ragEmpty) {
            log.info("GroundingChecker: both contexts empty for userId={}, returning neutral result", userId);
            return GroundingResult.builder()
                    .grounded(true)
                    .unsupportedClaims(new ArrayList<>())
                    .confidence(0.5)
                    .build();
        }

        String prompt = String.format("""
                请检查以下 Agent 输出中的每一条事实性陈述，是否能在提供的【用户记忆上下文】或【RAG 检索上下文】中找到依据。

                【Agent 输出（待核查）】
                %s

                【用户记忆上下文（Memory）】
                %s

                【RAG 检索上下文（Personal RAG）】
                %s

                【核查要求 — 必须严格遵守】
                1. 逐条扫描 Agent 输出中的事实性陈述（如「我做过 XX 项目」「我掌握 XX 技术」「公司要求 XX」等）。
                2. 对每条陈述，检查它是否能在上述【用户记忆上下文】或【RAG 检索上下文】中找到对应依据：
                   - 找到明确依据 → 视为有据可查
                   - 上下文中完全未提及 → 视为无据断言（编造），加入 unsupportedClaims
                   - 上下文中有矛盾证据（如记忆写"未做过 X"，输出却声称"做过 X"）→ 加入 unsupportedClaims
                3. grounded=true 当且仅当所有事实性陈述都有据可查；只要存在任何 unsupportedClaims，grounded=false。
                4. confidence 为本次核查结论的置信度（0.0~1.0）：
                   - 上下文充足且陈述清晰 → 0.85~1.0
                   - 上下文部分缺失但能判断 → 0.5~0.85
                   - 上下文严重不足或陈述模糊 → <0.5
                5. unsupportedClaims 中每条必须是 Agent 输出中具体的陈述原文或其概括（不可笼统说"存在幻觉"）。
                6. 若 Agent 输出中无可核查的事实性陈述（如纯主观建议、问候语），grounded=true，confidence=0.7，unsupportedClaims 为空数组。
                7. 输出必须是严格的 JSON 格式，不要输出 Markdown 代码块包裹。
                8. JSON 字符串值内部禁止使用未转义的双引号，如需引用文本请使用单引号（'）。

                【输出 JSON 格式】
                {"grounded": true, "unsupportedClaims": ["无据断言1", "无据断言2"], "confidence": 0.92}
                """, truncate(answer, 5000),
                memoryEmpty ? "（用户记忆上下文为空）" : truncate(memoryContext, 5000),
                ragEmpty ? "（RAG 检索上下文为空）" : truncate(ragContext, 5000));

        String fullPrompt = promptProvider.evaluationSystemPrompt() + "\n\n" + prompt;

        LLMCallContext.set(userId, null, "grounding_check");
        try {
            String response = chatLanguageModel.chat(fullPrompt);
            return parseGroundingResult(response);
        } catch (Exception e) {
            log.warn("GroundingChecker LLM call failed for userId={}, falling back to heuristic check: {}",
                    userId, e.getMessage());
            return heuristicCheck(answer, memoryContext, ragContext);
        } finally {
            LLMCallContext.clear();
        }
    }

    // ============================================================
    // 启发式降级检查（LLM 不可用时使用）
    // ============================================================

    /**
     * 启发式事实依据检查（LLM 降级路径）
     * <p>
     * 当 LLM 服务不可用（如账户欠费、网络异常）时，使用基于关键词匹配的
     * 启发式方法判断 answer 中的陈述是否有据可查，确保系统在 LLM 不可用时
     * 仍能提供基础的事实核查能力。
     * <p>
     * 算法：
     * 1. 将 answer 按标点分句
     * 2. 对每个句子提取关键词，检查是否在 memoryContext 或 ragContext 中出现
     * 3. 全部有据 → grounded=true, confidence=0.65（降级置信度）
     * 4. 存在无据句子 → grounded=false, unsupportedClaims=[无据句子], confidence=0.55
     *
     * @param answer        待核查的 Agent 输出
     * @param memoryContext 用户长期记忆上下文
     * @param ragContext    RAG 检索上下文
     * @return GroundingResult 启发式核查结果
     */
    private GroundingResult heuristicCheck(String answer, String memoryContext, String ragContext) {
        Set<String> evidenceKeys = new java.util.HashSet<>();
        evidenceKeys.addAll(extractKeywords(memoryContext));
        evidenceKeys.addAll(extractKeywords(ragContext));

        // 按句号、逗号等分割为细粒度子句，便于检测部分无据的情况
        List<String> answerClauses = splitClaims(answer);
        List<String> unsupportedClaims = new ArrayList<>();

        for (String clause : answerClauses) {
            Set<String> clauseKeys = extractKeywords(clause);
            if (clauseKeys.isEmpty()) {
                continue; // 无关键事实的子句跳过
            }
            boolean supported = clauseKeys.stream().anyMatch(evidenceKeys::contains);
            if (!supported) {
                unsupportedClaims.add(clause);
            }
        }

        boolean grounded = unsupportedClaims.isEmpty();
        double confidence = grounded ? 0.65 : 0.55;

        log.info("GroundingChecker heuristic result: grounded={}, unsupported={}, confidence={}",
                grounded, unsupportedClaims.size(), confidence);

        return GroundingResult.builder()
                .grounded(grounded)
                .unsupportedClaims(unsupportedClaims)
                .confidence(confidence)
                .build();
    }

    /**
     * 按句号、问号、感叹号、分号、逗号分割为细粒度子句
     * （比 splitSentences 更细，便于检测部分无据的陈述）
     */
    private static List<String> splitClaims(String text) {
        List<String> clauses = new ArrayList<>();
        if (text == null || text.isBlank()) return clauses;
        String[] parts = text.split("[。！？；，,!?,;\\n]");
        for (String p : parts) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty() && trimmed.length() >= 2) {
                clauses.add(trimmed);
            }
        }
        return clauses;
    }

    /**
     * 提取文本中的关键词（简易分词）
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

        // 中文关键词：按停用词和标点分割
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
     * 按句号、问号、感叹号、分号分割句子
     */
    private static List<String> splitSentences(String text) {
        List<String> sentences = new ArrayList<>();
        if (text == null || text.isBlank()) return sentences;
        String[] parts = text.split("[。！？；!?;\\n]");
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
            "对于", "关于", "根据", "作为", "属于", "使得", "从而", "以及", "等等"
    };

    // ============================================================
    // 解析与规范化
    // ============================================================

    /**
     * 解析 LLM 输出为 GroundingResult
     * <p>
     * 流程：
     * 1. LLMJsonSanitizer 清洗原始输出
     * 2. ObjectMapper 解析为 GroundingResult
     * 3. 若解析失败：降级为 Map 解析
     * 4. 若彻底解析失败：返回 grounded=false 的安全默认结果
     */
    private GroundingResult parseGroundingResult(String llmOutput) {
        if (llmOutput == null || llmOutput.isBlank()) {
            return defaultFailedResult();
        }

        // 路径 1：直接解析为 GroundingResult
        String clean = jsonSanitizer.sanitize(llmOutput);
        try {
            GroundingResult direct = objectMapper.readValue(clean, GroundingResult.class);
            if (direct != null && direct.getGrounded() != null) {
                normalizeResult(direct);
                return direct;
            }
        } catch (Exception e) {
            log.debug("GroundingResult direct parse failed: {}", e.getMessage());
        }

        // 路径 2：降级为 Map 手工提取
        try {
            Map<String, Object> map = objectMapper.readValue(clean, new TypeReference<Map<String, Object>>() {});
            GroundingResult fromMap = buildFromMap(map);
            if (fromMap != null) {
                normalizeResult(fromMap);
                return fromMap;
            }
        } catch (Exception e) {
            log.warn("GroundingResult parse failed completely, raw len={}: {}", clean.length(),
                    clean.substring(0, Math.min(200, clean.length())));
        }
        return defaultFailedResult();
    }

    /**
     * 从 Map 构建 GroundingResult（降级解析路径）
     */
    private GroundingResult buildFromMap(Map<String, Object> map) {
        Object groundedObj = map.get("grounded");
        if (groundedObj == null) return null;
        Boolean grounded;
        if (groundedObj instanceof Boolean b) {
            grounded = b;
        } else {
            grounded = Boolean.parseBoolean(groundedObj.toString());
        }

        List<String> unsupportedClaims = toStringList(map.get("unsupportedClaims"));
        Double confidence = toDouble(map.get("confidence"));

        return GroundingResult.builder()
                .grounded(grounded)
                .unsupportedClaims(unsupportedClaims)
                .confidence(confidence)
                .build();
    }

    /**
     * 规范化 GroundingResult：
     * - 钳制 confidence 在 0.0~1.0
     * - 不允许 grounded=true 同时存在 unsupportedClaims（若发生冲突，以 unsupportedClaims 为准）
     */
    private void normalizeResult(GroundingResult result) {
        if (result.getConfidence() == null) {
            result.setConfidence(0.5);
        } else {
            double c = result.getConfidence();
            if (c < 0.0) c = 0.0;
            if (c > 1.0) c = 1.0;
            result.setConfidence(c);
        }
        if (result.getUnsupportedClaims() == null) {
            result.setUnsupportedClaims(new ArrayList<>());
        }
        // 冲突修复：若 grounded=true 但存在 unsupportedClaims，以 unsupportedClaims 为准
        if (Boolean.TRUE.equals(result.getGrounded()) && !result.getUnsupportedClaims().isEmpty()) {
            result.setGrounded(false);
        }
    }

    /**
     * 构造解析失败时的安全默认结果（grounded=false，避免放行含幻觉的输出）
     */
    private GroundingResult defaultFailedResult() {
        return GroundingResult.builder()
                .grounded(false)
                .unsupportedClaims(List.of("检测异常"))
                .confidence(0.0)
                .build();
    }

    // ============================================================
    // 工具方法
    // ============================================================

    private static String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max) + "...（truncated " + (s.length() - max) + " chars）";
    }

    private static Double toDouble(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(v.toString());
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
    // DTO: 核查结果
    // ============================================================

    /**
     * 事实依据核查结果 DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroundingResult {
        /**
         * 是否全部事实性陈述都有据可查
         */
        private Boolean grounded;
        /**
         * 无据断言（编造的陈述）列表
         */
        private List<String> unsupportedClaims;
        /**
         * 核查结论的置信度 0.0~1.0
         */
        private Double confidence;
    }
}
