package com.focusos.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sprint 7-C-A: LLM JSON 输出清洗器
 * <p>
 * 解决问题：LLM 生成的 JSON 可能包含以下导致解析失败的内容：
 * 1. 正则表达式反斜杠（如 \\u4e00-\\u9fa5 未转义）
 * 2. 字符串内未转义的控制字符（\n, \t, \r）
 * 3. 字符串内未转义的双引号
 * 4. Markdown 代码块包裹（```json ... ```）
 * 5. JSON 字段值中包含未闭合的字符串（LLM 截断）
 * <p>
 * 策略：
 * 1. 预处理：去除 Markdown 包裹、修复常见转义问题
 * 2. 解析：使用 ObjectMapper 解析为 Map
 * 3. 修复：若解析失败，逐步截断到最后一个完整对象
 * 4. 序列化：通过 ObjectMapper.writeValueAsString 重新序列化，保证输出 JSON 100% 合法
 */
@Slf4j
@Component
public class LLMJsonSanitizer {

    private final ObjectMapper objectMapper;

    public LLMJsonSanitizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 清洗 LLM 输出为合法 JSON 字符串
     *
     * @param raw LLM 原始输出
     * @return 合法 JSON 字符串（若彻底无法解析，返回 "{}"）
     */
    public String sanitize(String raw) {
        if (raw == null || raw.isBlank()) return "{}";

        String cleaned = preprocess(raw);
        return sanitizeJsonString(cleaned);
    }

    /**
     * 清洗并解析为 Map
     */
    public Map<String, Object> sanitizeToMap(String raw) {
        String json = sanitize(raw);
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("sanitizeToMap failed after sanitization: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 清洗并解析为指定 DTO 类型
     */
    public <T> T sanitizeToObject(String raw, Class<T> clazz) {
        String json = sanitize(raw);
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            log.warn("sanitizeToObject({}) failed: {}", clazz.getSimpleName(), e.getMessage());
            return null;
        }
    }

    /**
     * 序列化对象为 JSON 字符串（保证输出合法）
     */
    public String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("serialize failed", e);
            return "{}";
        }
    }

    // ============================================================
    // 预处理：去除 Markdown 包裹、修复常见格式问题
    // ============================================================

    private String preprocess(String raw) {
        String s = raw.trim();

        // 1. 去除 Markdown 代码块包裹
        if (s.startsWith("```")) {
            int start = s.indexOf('\n');
            if (start >= 0) {
                s = s.substring(start + 1);
            }
            int end = s.lastIndexOf("```");
            if (end >= 0) {
                s = s.substring(0, end);
            }
            s = s.trim();
        }

        // 2. 提取 JSON 边界（{ ... }）
        int firstBrace = s.indexOf('{');
        int lastBrace = s.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            s = s.substring(firstBrace, lastBrace + 1);
        }

        return s;
    }

    // ============================================================
    // JSON 字符串清洗：修复转义、截断修复
    // ============================================================

    private String sanitizeJsonString(String json) {
        // 第一次尝试：直接解析
        try {
            objectMapper.readTree(json);
            return json;
        } catch (Exception ignored) {
            // 继续修复
        }

        // 2. 修复字符串内未转义的控制字符
        String fixed = fixControlChars(json);
        try {
            objectMapper.readTree(fixed);
            return fixed;
        } catch (Exception ignored) {
        }

        // 3. 修复字符串内未转义的反斜杠（如正则表达式 \d, \u4e00）
        fixed = fixBackslashes(json);
        try {
            objectMapper.readTree(fixed);
            return fixed;
        } catch (Exception ignored) {
        }

        // 3.5 Sprint 8-A: 修复字符串内未转义的双引号（LLM 常见输出问题）
        // 当 LLM 在 JSON 字符串值中使用 "..." 引用时，内部的双引号未转义导致解析失败
        fixed = fixUnescapedQuotes(json);
        try {
            objectMapper.readTree(fixed);
            return fixed;
        } catch (Exception ignored) {
        }
        // 组合修复：控制字符 + 反斜杠 + 未转义双引号
        fixed = fixUnescapedQuotes(fixBackslashes(fixControlChars(json)));
        try {
            objectMapper.readTree(fixed);
            return fixed;
        } catch (Exception ignored) {
        }

        // 3.6 Sprint 8-A: 修复括号不匹配（LLM 用 } 关闭数组或用 ] 关闭对象）
        fixed = fixMismatchedBrackets(json);
        try {
            objectMapper.readTree(fixed);
            return fixed;
        } catch (Exception ignored) {
        }
        // 组合修复：控制字符 + 反斜杠 + 未转义双引号 + 括号不匹配
        fixed = fixMismatchedBrackets(fixUnescapedQuotes(fixBackslashes(fixControlChars(json))));
        try {
            objectMapper.readTree(fixed);
            return fixed;
        } catch (Exception ignored) {
        }

        // 4. 截断到最后一个完整的对象（LLM 输出截断时的兜底）
        String truncated = truncateToLastComplete(json);
        if (truncated != null && !truncated.equals(json)) {
            try {
                objectMapper.readTree(truncated);
                return truncated;
            } catch (Exception ignored) {
            }
            // 对截断后的内容再次尝试修复
            String truncatedFixed = fixBackslashes(truncated);
            try {
                objectMapper.readTree(truncatedFixed);
                return truncatedFixed;
            } catch (Exception ignored) {
            }
            // Sprint 8-A: 截断后也尝试修复未转义双引号
            truncatedFixed = fixUnescapedQuotes(fixBackslashes(fixControlChars(truncated)));
            try {
                objectMapper.readTree(truncatedFixed);
                return truncatedFixed;
            } catch (Exception ignored) {
            }
            // Sprint 8-A: 截断后也尝试修复括号不匹配
            truncatedFixed = fixMismatchedBrackets(fixUnescapedQuotes(fixBackslashes(fixControlChars(truncated))));
            try {
                objectMapper.readTree(truncatedFixed);
                return truncatedFixed;
            } catch (Exception ignored) {
            }
        }

        // 5. 正则提取数组元素重建 JSON（处理字符串内未转义双引号导致整体解析失败）
        String rebuilt = rebuildFromArrayElements(json);
        if (rebuilt != null) {
            try {
                objectMapper.readTree(rebuilt);
                return rebuilt;
            } catch (Exception ignored) {
            }
        }

        // 6. 终极兜底：返回空对象
        log.warn("JSON sanitization exhausted, returning empty object. Original length={}, preview={}",
                json.length(), json.substring(0, Math.min(500, json.length())));
        return "{}";
    }

    /**
     * 修复字符串内未转义的控制字符
     * 将 JSON 字符串值内的裸 \n \r \t 替换为转义形式
     */
    private String fixControlChars(String json) {
        // 仅修复字符串内部的控制字符
        StringBuilder sb = new StringBuilder();
        boolean inString = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) {
                inString = !inString;
                sb.append(c);
            } else if (inString) {
                switch (c) {
                    case '\n' -> sb.append("\\n");
                    case '\r' -> sb.append("\\r");
                    case '\t' -> sb.append("\\t");
                    default -> sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 修复字符串内未转义的反斜杠
     * <p>
     * LLM 在 expectedAnswer 中可能输出正则表达式如 [\u4e00-\u9fa5]，
     * 其中 \u4e00 会被 JSON 解析器误认为 Unicode 转义，导致解析失败。
     * <p>
     * 策略：扫描字符串值内的反斜杠，若后跟非 JSON 合法转义字符（"\/bfnrtu），
     * 则将反斜杠转义为 \\。
     */
    private String fixBackslashes(String json) {
        StringBuilder sb = new StringBuilder();
        boolean inString = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) {
                inString = !inString;
                sb.append(c);
            } else if (inString && c == '\\') {
                // 检查反斜杠后是否为合法 JSON 转义字符
                if (i + 1 < json.length()) {
                    char next = json.charAt(i + 1);
                    if (next == '"' || next == '\\' || next == '/' || next == 'b'
                            || next == 'f' || next == 'n' || next == 'r' || next == 't' || next == 'u') {
                        // 合法转义，原样保留
                        sb.append(c);
                    } else {
                        // 非法转义（如正则 \d, \s），将 \ 转义为 \\
                        sb.append("\\\\");
                    }
                } else {
                    // 反斜杠在字符串末尾，转义
                    sb.append("\\\\");
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Sprint 8-A: 修复字符串内未转义的双引号
     * <p>
     * LLM 常见输出问题：在 JSON 字符串值中使用 "..." 引用文本时，
     * 内部的双引号未转义为 \"，导致 JSON 解析器误认为字符串提前结束。
     * <p>
     * 例如：{"weaknesses": ["JD 要求"高并发"经验"]} 会被误解析为字符串 "JD 要求" + 高并发 + "经验"
     * <p>
     * 策略：逐字符扫描 JSON，当在字符串内部遇到 " 时，向前查看下一个非空白字符：
     * - 若为 JSON 结构字符（, } ] :）或到达末尾 → 这是字符串的闭合引号
     * - 否则 → 这是字符串内部的未转义引号，转义为 \"
     */
    private String fixUnescapedQuotes(String json) {
        if (json == null || json.length() < 2) return json;

        StringBuilder sb = new StringBuilder(json.length() + 64);
        boolean inString = false;
        int len = json.length();

        for (int i = 0; i < len; i++) {
            char c = json.charAt(i);

            if (c == '\\' && inString) {
                // 反斜杠转义：原样保留当前字符和下一个字符
                sb.append(c);
                if (i + 1 < len) {
                    sb.append(json.charAt(i + 1));
                    i++;
                }
                continue;
            }

            if (c == '"') {
                if (!inString) {
                    // 进入字符串
                    inString = true;
                    sb.append(c);
                } else {
                    // 在字符串内遇到双引号，判断是闭合引号还是未转义的内部引号
                    // 向前查看下一个非空白字符
                    int j = i + 1;
                    while (j < len && Character.isWhitespace(json.charAt(j))) {
                        j++;
                    }
                    if (j >= len) {
                        // 到达末尾，这是闭合引号
                        inString = false;
                        sb.append(c);
                    } else {
                        char next = json.charAt(j);
                        if (next == ',' || next == '}' || next == ']' || next == ':') {
                            // 下一个非空白字符是 JSON 结构字符 → 闭合引号
                            inString = false;
                            sb.append(c);
                        } else {
                            // 下一个非空白字符不是结构字符 → 未转义的内部引号
                            sb.append("\\\"");
                        }
                    }
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Sprint 8-A: 修复括号不匹配
     * <p>
     * LLM 常见输出问题：用 } 关闭数组（应为 ]），或用 ] 关闭对象（应为 }）。
     * 例如：{"suggestions": ["a", "b"} → 应为 {"suggestions": ["a", "b"]}
     * <p>
     * 策略：使用栈跟踪当前上下文（对象 vs 数组），遇到闭合括号时检查是否匹配：
     * - } 且栈顶是数组 → 替换为 ]
     * - ] 且栈顶是对象 → 替换为 }
     */
    private String fixMismatchedBrackets(String json) {
        if (json == null || json.length() < 2) return json;

        StringBuilder sb = new StringBuilder(json.length());
        Deque<Boolean> stack = new ArrayDeque<>(); // true = object {}, false = array []
        boolean inString = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            // 处理字符串内的转义字符
            if (c == '\\' && inString) {
                sb.append(c);
                if (i + 1 < json.length()) {
                    sb.append(json.charAt(i + 1));
                    i++;
                }
                continue;
            }

            // 跟踪字符串状态
            if (c == '"') {
                inString = !inString;
                sb.append(c);
                continue;
            }

            if (inString) {
                sb.append(c);
                continue;
            }

            // 在字符串外：跟踪括号匹配
            if (c == '{') {
                stack.push(true);
                sb.append(c);
            } else if (c == '[') {
                stack.push(false);
                sb.append(c);
            } else if (c == '}') {
                if (!stack.isEmpty() && !stack.peek()) {
                    // 栈顶是数组，但遇到 } → 应为 ]
                    sb.append(']');
                    stack.pop();
                } else {
                    sb.append(c);
                    if (!stack.isEmpty()) stack.pop();
                }
            } else if (c == ']') {
                if (!stack.isEmpty() && stack.peek()) {
                    // 栈顶是对象，但遇到 ] → 应为 }
                    sb.append('}');
                    stack.pop();
                } else {
                    sb.append(c);
                    if (!stack.isEmpty()) stack.pop();
                }
            } else {
                sb.append(c);
            }
        }

        return sb.toString();
    }

    /**
     * 截断到最后一个完整的对象（用于 LLM 输出被截断的情况）
     * <p>
     * 策略：从后向前查找 "}," 或 "}]"，截断到该位置并补全闭合括号。
     */
    private String truncateToLastComplete(String json) {
        if (json == null || json.length() < 10) return null;

        // 查找最后一个完整对象的结束位置（通过 "}," 或 "}" + 换行 + 空格）
        // 在数组场景下：{ "interviewQuestions": [ {...}, {...}, {...}
        // 我们要找到最后一个完整的 {...}
        int lastCompleteObj = findLastCompleteObject(json);
        if (lastCompleteObj < 0) return null;

        String prefix = json.substring(0, lastCompleteObj + 1).trim();

        // 推断需要补全的闭合括号
        int openBraces = countChar(prefix, '{');
        int closeBraces = countChar(prefix, '}');
        int openBrackets = countChar(prefix, '[');
        int closeBrackets = countChar(prefix, ']');

        StringBuilder result = new StringBuilder(prefix);
        // 补全缺失的 }
        for (int i = 0; i < openBraces - closeBraces; i++) {
            result.append('}');
        }
        // 补全缺失的 ]
        for (int i = 0; i < openBrackets - closeBrackets; i++) {
            result.append(']');
        }
        // 再补一个 }（外层对象）
        // 检查是否需要：如果原始结构是 { "xxx": [ {...} ] }，prefix 可能已包含足够的 }
        // 但若 prefix 是 { "xxx": [ {...}, {...}，则需补 ] }
        // 上面已处理

        String candidate = result.toString();
        try {
            objectMapper.readTree(candidate);
            return candidate;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 找到最后一个完整对象的结束位置（} 后跟 , 或 ] 或换行）
     */
    private int findLastCompleteObject(String json) {
        // 从后向前找 '}'
        for (int i = json.length() - 1; i >= 0; i--) {
            if (json.charAt(i) == '}') {
                // 检查这个 } 之后是否是 , 或 ] 或空白
                int j = i + 1;
                while (j < json.length() && Character.isWhitespace(json.charAt(j))) j++;
                if (j >= json.length() || json.charAt(j) == ',' || json.charAt(j) == ']') {
                    return i;
                }
            }
        }
        return -1;
    }

    private int countChar(String s, char c) {
        int count = 0;
        boolean inString = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '"' && (i == 0 || s.charAt(i - 1) != '\\')) {
                inString = !inString;
            } else if (!inString && ch == c) {
                count++;
            }
        }
        return count;
    }

    /**
     * Sprint 7-C-B: 正则提取数组元素重建 JSON
     * <p>
     * 当整体 JSON 因字符串内未转义双引号等复杂问题无法解析时，
     * 尝试逐个提取数组中的 {...} 元素，对每个元素单独清洗后重建数组。
     * <p>
     * 策略：
     * 1. 找到 "interviewQuestions": [ 或类似的数组结构
     * 2. 用大括号匹配逐个提取 {...} 元素
     * 3. 对每个元素尝试 fixControlChars + fixBackslashes + truncate
     * 4. 重建为 { "interviewQuestions": [ {...}, {...} ] }
     */
    private String rebuildFromArrayElements(String json) {
        if (json == null || json.length() < 20) return null;

        // 找到数组开始位置（匹配 "key": [ 模式）
        int arrStart = -1;
        String arrKey = null;
        Pattern arrPattern = Pattern.compile("\"([a-zA-Z]+)\"\\s*:\\s*\\[");
        Matcher m = arrPattern.matcher(json);
        while (m.find()) {
            arrKey = m.group(1);
            arrStart = m.end();
            break;
        }
        if (arrStart < 0) return null;

        // 用大括号匹配提取数组中的 {...} 元素
        List<String> elements = new ArrayList<>();
        int i = arrStart;
        while (i < json.length()) {
            // 跳过空白和逗号
            while (i < json.length() && (Character.isWhitespace(json.charAt(i)) || json.charAt(i) == ',' )) i++;
            if (i >= json.length() || json.charAt(i) == ']') break;
            if (json.charAt(i) != '{') break;

            // 大括号匹配提取一个完整对象
            int depth = 0;
            int start = i;
            boolean inStr = false;
            while (i < json.length()) {
                char c = json.charAt(i);
                if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) {
                    inStr = !inStr;
                } else if (!inStr) {
                    if (c == '{') depth++;
                    else if (c == '}') {
                        depth--;
                        if (depth == 0) { i++; break; }
                    }
                }
                i++;
            }
            String element = json.substring(start, i);
            // 尝试解析单个元素
            String fixedElement = fixSingleElement(element);
            if (fixedElement != null) {
                elements.add(fixedElement);
            }
        }

        if (elements.isEmpty()) return null;

        // 重建 JSON
        StringBuilder sb = new StringBuilder();
        sb.append("{\"").append(arrKey).append("\":[");
        for (int j = 0; j < elements.size(); j++) {
            if (j > 0) sb.append(",");
            sb.append(elements.get(j));
        }
        sb.append("]}");

        return sb.toString();
    }

    /**
     * 尝试修复单个 JSON 对象元素
     */
    private String fixSingleElement(String element) {
        // 尝试直接解析
        try {
            objectMapper.readTree(element);
            return element;
        } catch (Exception ignored) {
        }
        // 尝试 fixControlChars
        String fixed = fixControlChars(element);
        try {
            objectMapper.readTree(fixed);
            return fixed;
        } catch (Exception ignored) {
        }
        // 尝试 fixBackslashes
        fixed = fixBackslashes(element);
        try {
            objectMapper.readTree(fixed);
            return fixed;
        } catch (Exception ignored) {
        }
        // 组合修复
        fixed = fixBackslashes(fixControlChars(element));
        try {
            objectMapper.readTree(fixed);
            return fixed;
        } catch (Exception ignored) {
        }
        // 提取可识别的字段重建对象（兜底）
        return rebuildFromKnownFields(element);
    }

    /**
     * 从元素中提取已知字段重建对象（最终兜底）
     */
    private String rebuildFromKnownFields(String element) {
        try {
            Map<String, Object> obj = new java.util.LinkedHashMap<>();
            // 提取字符串字段
            for (String field : new String[]{"type","category","question","difficulty","expectedAnswer","userProjectReference"}) {
                Pattern p = Pattern.compile("\"" + field + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
                Matcher fm = p.matcher(element);
                if (fm.find()) {
                    String value = fm.group(1);
                    // 反转义常见序列
                    value = value.replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"").replace("\\\\", "\\");
                    obj.put(field, value);
                }
            }
            // 提取数字字段
            for (String field : new String[]{"score"}) {
                Pattern p = Pattern.compile("\"" + field + "\"\\s*:\\s*(\\d+)");
                Matcher fm = p.matcher(element);
                if (fm.find()) {
                    obj.put(field, Integer.parseInt(fm.group(1)));
                }
            }
            // 提取 followUpQuestions 数组
            Pattern arrP = Pattern.compile("\"followUpQuestions\"\\s*:\\s*\\[([^\\]]*)\\]");
            Matcher am = arrP.matcher(element);
            if (am.find()) {
                List<String> items = new ArrayList<>();
                Pattern itemP = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");
                Matcher im = itemP.matcher(am.group(1));
                while (im.find()) {
                    items.add(im.group(1).replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\"));
                }
                obj.put("followUpQuestions", items);
            }
            if (obj.isEmpty() || !obj.containsKey("question")) return null;
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }
}
