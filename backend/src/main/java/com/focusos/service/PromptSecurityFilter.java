package com.focusos.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Sprint 8-E: Prompt Injection 防护过滤器 (Task 13)
 * <p>
 * 在用户输入进入 LLM 调用前进行安全扫描，检测常见的 Prompt Injection 攻击模式：
 * <ul>
 *   <li>覆盖系统指令：{@code ignore previous instruction} / {@code forget your rules}</li>
 *   <li>角色劫持：{@code you are now...} / {@code act as...} / {@code pretend to be}</li>
 *   <li>系统提示词泄露：{@code show me your system prompt} / {@code reveal your instructions}</li>
 *   <li>越狱指令：{@code jailbreak} / {@code DAN mode} / {@code developer mode}</li>
 *   <li>分隔符注入：{@code <|im_start|>} / {@code <|system|>} 等 ChatML 标记</li>
 * </ul>
 * <p>
 * 检测策略：
 * <ul>
 *   <li>匹配到任一已知攻击模式 → 返回 {@link ScanResult#blocked}={@code true} 并给出命中规则</li>
 *   <li>未匹配 → 返回 {@code blocked=false}，允许通过</li>
 *   <li>所有匹配均为大小写不敏感，并先做 Unicode 归一化（NFKC）以抵御全角字符绕过</li>
 * </ul>
 */
@Slf4j
@Service
public class PromptSecurityFilter {

    /** 已知 Prompt Injection 攻击模式（小写匹配） */
    private static final List<Pattern> ATTACK_PATTERNS = List.of(
            // 覆盖系统指令
            Pattern.compile("ignore\\s+(all\\s+)?(previous|prior|above)\\s+(instructions?|prompts?|rules?)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("forget\\s+(your|all|the)\\s+(instructions?|rules?|prompts?)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("disregard\\s+(all\\s+)?(previous|prior)\\s+(instructions?|rules?)", Pattern.CASE_INSENSITIVE),
            // 角色劫持
            Pattern.compile("you\\s+are\\s+now\\s+(a|an)?\\s*(dan|developer|admin|root)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("act\\s+as\\s+(if\\s+you\\s+are\\s+)?(a|an)?\\s*(dan|developer|admin|root|jailbreaker)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("pretend\\s+to\\s+be\\s+(a|an)?\\s*(dan|developer|admin|root|jailbreaker)", Pattern.CASE_INSENSITIVE),
            // 系统提示词泄露
            Pattern.compile("(show|reveal|print|display|repeat)\\s+(me\\s+)?(your|the)\\s+(system\\s+)?(prompt|instructions?|rules?|initial\\s+message)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("what\\s+(is|are)\\s+your\\s+(system\\s+)?(prompt|instructions?|rules?)", Pattern.CASE_INSENSITIVE),
            // 越狱模式
            Pattern.compile("\\bdan\\s+mode\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bdeveloper\\s+mode\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bjailbreak\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bstan\\s+mode\\b", Pattern.CASE_INSENSITIVE),
            // ChatML / 分隔符注入
            Pattern.compile("<\\|im_start\\|>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<\\|im_end\\|>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<\\|system\\|>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<\\|assistant\\|>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<\\|user\\|>", Pattern.CASE_INSENSITIVE),
            // 通用提示词劫持
            Pattern.compile("from\\s+now\\s+on[,\\s]+you\\s+(are|will)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("new\\s+instructions?\\s*:", Pattern.CASE_INSENSITIVE),
            // 角色重置（中文变体）
            Pattern.compile("\u5ffd\u7565\u4e4b\u524d\u7684(\u6307\u4ee4|\u89c4\u5219|\u63d0\u793a)"), // 忽略之前的(指令|规则|提示)
            Pattern.compile("\u4f60\u73b0\u5728\u662f(DAN|\u5f00\u53d1\u8005|\u7ba1\u7406\u5458)"), // 你现在是(DAN|开发者|管理员)
            Pattern.compile("\u91ca\u653e\u4f60\u7684(\u7cfb\u7edf\u63d0\u793a\u8bcd|\u521d\u59cb\u6307\u4ee4)") // 释放你的(系统提示词|初始指令)
    );

    /** 单次输入最大长度限制（防 DoS，超过则截断后扫描） */
    private static final int MAX_SCAN_LENGTH = 32_000;

    /**
     * 扫描用户输入，检测是否包含 Prompt Injection 攻击。
     *
     * @param input 用户输入文本
     * @return 扫描结果（{@code blocked=true} 表示拦截，{@code reason} 给出命中规则）
     */
    public ScanResult scan(String input) {
        if (input == null || input.isBlank()) {
            return ScanResult.allowed();
        }

        // Unicode 归一化（NFKC）+ 大小写不敏感匹配，抵御全角字符绕过
        String normalized = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFKC);
        if (normalized.length() > MAX_SCAN_LENGTH) {
            normalized = normalized.substring(0, MAX_SCAN_LENGTH);
        }

        for (int i = 0; i < ATTACK_PATTERNS.size(); i++) {
            Pattern pattern = ATTACK_PATTERNS.get(i);
            if (pattern.matcher(normalized).find()) {
                String reason = String.format("命中 Prompt Injection 规则 #%d: %s", i + 1, pattern.pattern());
                log.warn("PromptSecurityFilter 拦截可疑输入: rule={}, length={}", i + 1, input.length());
                return ScanResult.blocked(reason);
            }
        }

        return ScanResult.allowed();
    }

    /**
     * 便捷方法：扫描并返回是否允许通过。
     */
    public boolean isAllowed(String input) {
        return !scan(input).isBlocked();
    }

    /**
     * 扫描结果。
     */
    public record ScanResult(boolean blocked, String reason) {
        public static ScanResult allowed() {
            return new ScanResult(false, null);
        }

        public static ScanResult blocked(String reason) {
            return new ScanResult(true, reason);
        }

        public boolean isBlocked() {
            return blocked;
        }
    }
}
