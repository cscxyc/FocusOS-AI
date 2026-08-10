package com.focusos.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.focusos.entity.UserMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Sprint 8-C: Memory Agent — 长期记忆提取专家
 * <p>
 * 职责：从用户行为事件中自动提取结构化的长期记忆，
 * 输入为 {eventType, content} 事件，输出为 List<UserMemory> 结构。
 * <p>
 * 核心能力：
 * 1. 不编造技能/项目（严格基于输入内容，5 条质量约束）
 * 2. confidence 证据强度打分（高证据 0.9~1.0 / 间接推断 ≤0.6）
 * 3. 多记忆拆分：一个项目完成 → 拆成 PROJECT + 若干 SKILL 条目
 * 4. 严格 JSON 数组输出：LLMJsonSanitizer 多层清洗 + 降级 Map 解析
 * <p>
 * agentType = "memory"，通过 LoggingChatLanguageModel 自动记录 observability
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryAgent implements FocusAgent {

    private final ChatLanguageModel chatLanguageModel;
    private final AgentPromptProvider promptProvider;
    private final LLMJsonSanitizer jsonSanitizer;
    private final ObjectMapper objectMapper;

    @Override
    public String type() {
        return "memory";
    }

    /**
     * 实现 FocusAgent 接口，走通用路由（此处简单包装主方法）
     */
    @Override
    public String handle(String message, Long userId, String context) {
        try {
            // 把 message 当 content 处理；如果是 JSON 格式优先解析 eventType
            String eventType = "FEEDBACK";
            String content = message;
            try {
                Map<String, Object> m = new ObjectMapper().readValue(message, new TypeReference<Map<String, Object>>() {});
                if (m.get("eventType") != null) eventType = m.get("eventType").toString();
                if (m.get("content") != null) content = m.get("content").toString();
            } catch (Exception ignore) {}
            List<UserMemory> result = extractMemories(eventType, content, userId);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("MemoryAgent handle failed", e);
            return "[]";
        }
    }

    // ============================================================
    // 核心业务方法：从事件中提取记忆列表
    // ============================================================

    /**
     * 从用户行为事件中提取结构化长期记忆
     *
     * @param eventType   事件类型：LEARNING_COMPLETED / PROJECT_COMPLETED / INTERVIEW_FEEDBACK / FEEDBACK 等
     * @param content     事件内容：自由文本（项目完成描述、学习总结、面试复盘）
     * @param userId      用户 ID（仅用于 observability 记录，结果不带 userId，由 service 层注入）
     * @return 提取出的记忆列表（尚未落库，confidence / memoryType / memoryKey / memoryValue 已填充）
     */
    public List<UserMemory> extractMemories(String eventType, String content, Long userId) {
        if (content == null || content.isBlank()) {
            log.warn("MemoryAgent extract empty content for userId={}", userId);
            return new ArrayList<>();
        }
        String eventTag = eventType == null ? "FEEDBACK" : eventType;

        String prompt = String.format("""
                请从以下用户行为事件中提取结构化长期记忆。

                【事件类型】
                %s

                【事件内容（用户真实行为证据）】
                %s

                【提取规则 — 必须严格遵守】
                1. 绝不编造：所有 memoryValue 必须来自上述事件内容中的明确证据，禁止臆测、推断、扩展任何未提及的能力。
                2. 拆分粒度：不同的技能/项目必须拆分为独立条目；例如完成一个项目 → 至少产出 1 条 PROJECT 记忆 + 若干 SKILL 记忆。
                3. confidence 判断：
                   - 用户明确自报"完成了XX"或系统任务完成回调 → 0.90~1.0
                   - 基于事件内容的明确归纳 → 0.70~0.89
                   - 间接提及或不充分证据 → 0.30~0.69
                   - 完全不确定 → 不生成该条（宁可漏，不可编造）
                4. memoryKey 简洁明确：技能名用标准名称，如 "Milvus"、"Spring Cloud Gateway"、"RAG系统设计"。
                5. 如果内容完全是闲聊、空泛自夸、或没有任何可验证的事实，输出空数组 []。
                6. 【重要】禁止输出 Markdown 代码块。只输出纯 JSON 数组。字符串值内部严禁使用未转义的双引号，用单引号替代。

                输出严格按如下 JSON 数组格式（每条包含 memoryType / memoryKey / memoryValue / confidence）：
                [
                  {
                    "memoryType": "SKILL",
                    "memoryKey": "Milvus",
                    "memoryValue": "完成向量检索优化实验，掌握HNSW索引、分区策略、metadata过滤",
                    "confidence": 0.95
                  }
                ]
                """, eventTag, truncate(content, 5000));

        String fullPrompt = promptProvider.memorySystemPrompt() + "\n\n" + prompt;

        try {
            String response = chatLanguageModel.chat(fullPrompt);
            List<UserMemory> result = parseMemoryList(response);

            // 首次解析失败重试（追加 JSON 格式提醒）
            if (result == null || result.isEmpty()) {
                String retryPrompt = fullPrompt + "\n\n【重要提醒】你上次的输出不符合 JSON 数组格式。请严格按 [{...},{...}] 数组格式重新输出。绝对不要输出 ```json 代码块包裹。字符串内部不要用双引号，用单引号替代。";
                String retryResp = chatLanguageModel.chat(retryPrompt);
                List<UserMemory> retry = parseMemoryList(retryResp);
                if (retry != null && !retry.isEmpty()) result = retry;
            }

            // 注入 eventType 来源
            if (result != null) {
                for (UserMemory m : result) {
                    if (m.getSource() == null || m.getSource().isBlank()) {
                        m.setSource(eventTag + "_EVENT");
                    }
                    if (m.getConfidence() == null) m.setConfidence(0.7);
                    if (m.getConfidence() < 0.0) m.setConfidence(0.0);
                    if (m.getConfidence() > 1.0) m.setConfidence(1.0);
                }
                return result;
            }
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("MemoryAgent extractMemories LLM call failed for userId={}", userId, e);
            return new ArrayList<>();
        }
    }

    // ============================================================
    // 解析工具：LLMJsonSanitizer 清洗 + ObjectMapper 解析 + 降级 Map 提取
    // ============================================================

    private List<UserMemory> parseMemoryList(String llmOutput) {
        if (llmOutput == null || llmOutput.isBlank()) return new ArrayList<>();

        // 路径 1：直接解析为 List<UserMemory>
        String clean = jsonSanitizer.sanitize(llmOutput);
        try {
            List<UserMemory> direct = objectMapper.readValue(clean, new TypeReference<List<UserMemory>>() {});
            return normalizeMemories(direct);
        } catch (Exception e) {
            log.debug("MemoryAgent direct List<UserMemory> parse failed: {}", e.getMessage());
        }

        // 路径 2：降级为 List<Map> 手工提取
        List<Map<String, Object>> maps = null;
        try {
            maps = objectMapper.readValue(clean, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            // 路径 3：单个对象包成 List
            try {
                Map<String, Object> single = objectMapper.readValue(clean, new TypeReference<Map<String, Object>>() {});
                maps = List.of(single);
            } catch (Exception e2) {
                log.warn("MemoryAgent parse failed completely, raw len={}: {}", clean.length(), clean.substring(0, Math.min(200, clean.length())));
                return new ArrayList<>();
            }
        }

        List<UserMemory> result = new ArrayList<>();
        if (maps == null) return result;
        for (Map<String, Object> m : maps) {
            UserMemory mem = fromMap(m);
            if (mem != null) result.add(mem);
        }
        return normalizeMemories(result);
    }

    private UserMemory fromMap(Map<String, Object> m) {
        String type = getStr(m, "memoryType");
        String key = getStr(m, "memoryKey");
        String value = getStr(m, "memoryValue");
        if (key == null || value == null) return null;
        // 合法类型归一化
        Set<String> VALID = Set.of("SKILL","PROJECT","EXPERIENCE","GOAL","LEARNING_PROGRESS","PREFERENCE","ACHIEVEMENT");
        if (type == null) type = "SKILL";
        String up = type.toUpperCase();
        if (!VALID.contains(up)) up = "SKILL";
        Double conf = getDouble(m, "confidence");
        String source = getStr(m, "source");
        return UserMemory.builder()
                .memoryType(up)
                .memoryKey(key)
                .memoryValue(value)
                .confidence(conf)
                .source(source)
                .build();
    }

    private List<UserMemory> normalizeMemories(List<UserMemory> input) {
        List<UserMemory> result = new ArrayList<>();
        Set<String> VALID = Set.of("SKILL","PROJECT","EXPERIENCE","GOAL","LEARNING_PROGRESS","PREFERENCE","ACHIEVEMENT");
        if (input == null) return result;
        for (UserMemory m : input) {
            if (m.getMemoryKey() == null || m.getMemoryKey().isBlank()) continue;
            if (m.getMemoryValue() == null || m.getMemoryValue().isBlank()) continue;
            if (m.getMemoryType() == null || !VALID.contains(m.getMemoryType().toUpperCase())) {
                m.setMemoryType("SKILL");
            } else {
                m.setMemoryType(m.getMemoryType().toUpperCase());
            }
            if (m.getConfidence() == null) m.setConfidence(0.7);
            if (m.getConfidence() < 0) m.setConfidence(0.0);
            if (m.getConfidence() > 1.0) m.setConfidence(1.0);
            if (m.getMemoryKey().length() > 100) {
                m.setMemoryKey(m.getMemoryKey().substring(0, 100));
            }
            result.add(m);
        }
        return result;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max) + "...（truncated " + (s.length() - max) + " chars）";
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    private static String getStr(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : v.toString();
    }
    private static Double getDouble(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return null;
        try {
            if (v instanceof Number n) return n.doubleValue();
            return Double.parseDouble(v.toString());
        } catch (Exception e) { return null; }
    }
}
