package com.focusos.service;

import com.focusos.entity.UserMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Sprint 8-C: 记忆合并策略
 * <p>
 * 同用户 + 同 memoryType + 同 memoryKey 的记忆，
 * 不重复新增，而是合并内容 + confidence 取高，
 * 避免一条技能在数据库中出现 10 条 "Milvus 学习基础 / 进阶 / 高级..."
 * <p>
 * 合并规则：
 * 1. SKILL：累加经验描述 → "已掌握基础：旧描述；并进阶实践：新描述"
 * 2. PROJECT：追加里程碑 → "里程碑1；里程碑2；里程碑3"
 * 3. LEARNING_PROGRESS：更新进度描述，优先保留更近期 / 百分比更高的内容
 * 4. 其他类型（EXPERIENCE/GOAL/PREFERENCE/ACHIEVEMENT）：去重后合并短句，保留较新内容
 * 5. confidence：取 max(旧, 新)，并 +0.02（合并事件本身强化了证据），上限 1.0
 */
@Slf4j
@Component
public class MemoryMergeStrategy {

    /**
     * 判断是否可以合并（类型 + key 都相同才合并）
     */
    public boolean canMerge(UserMemory existing, UserMemory incoming) {
        if (existing == null || incoming == null) return false;
        return eq(existing.getMemoryType(), incoming.getMemoryType())
                && eq(normalizeKey(existing.getMemoryKey()), normalizeKey(incoming.getMemoryKey()));
    }

    /**
     * 执行合并：existing 作为基底实体（将被 JPA 持久化更新 updatedAt），
     * incoming 是新记忆内容，返回 merged existing（同一实体引用）。
     */
    public UserMemory merge(UserMemory existing, UserMemory incoming) {
        if (existing == null) return incoming;
        if (incoming == null) return existing;

        // 1. 合并 memoryValue
        String mergedValue = mergeValue(
                existing.getMemoryType(),
                existing.getMemoryValue(),
                incoming.getMemoryValue()
        );
        existing.setMemoryValue(mergedValue);

        // 2. confidence：取 max + 0.02（合并事件强化证据），上限 1.0
        double oldConf = existing.getConfidence() == null ? 0.6 : existing.getConfidence();
        double newConf = incoming.getConfidence() == null ? 0.6 : incoming.getConfidence();
        double merged = Math.min(1.0, Math.max(oldConf, newConf) + 0.02);
        existing.setConfidence(rounded(merged));

        // 3. source：追加（用 "|" 分隔，避免覆盖原始证据来源）
        if (incoming.getSource() != null && !incoming.getSource().isBlank()) {
            if (existing.getSource() == null || existing.getSource().isBlank()) {
                existing.setSource(incoming.getSource());
            } else if (!existing.getSource().contains(incoming.getSource())) {
                existing.setSource(existing.getSource() + "|" + incoming.getSource());
            }
        }

        // 4. updatedAt 在 PreUpdate 钩子自动更新；createdAt 不变
        return existing;
    }

    // ============================================================
    // 按 memoryType 分类合并 value
    // ============================================================

    private String mergeValue(String type, String oldValue, String newValue) {
        if (isBlank(oldValue)) return defaultValueFor(newValue);
        if (isBlank(newValue)) return oldValue;

        String s1 = stripTrailingPunct(oldValue.trim());
        String s2 = stripTrailingPunct(newValue.trim());

        // 完全相同 → 不重复
        if (s1.equalsIgnoreCase(s2)) return oldValue;

        // 新内容被旧内容包含（s1 已经包含 s2 的核心信息）→ 返回旧的
        if (s1.length() >= s2.length() && fuzzyContains(s1, s2)) return oldValue;
        // 旧内容被新内容完全包含 → 返回新的
        if (s2.length() > s1.length() && fuzzyContains(s2, s1)) return newValue;

        // 根据类型合并
        return switch (type == null ? "SKILL" : type.toUpperCase()) {
            case "SKILL" -> mergeSkill(s1, s2);
            case "PROJECT" -> mergeProject(s1, s2);
            case "LEARNING_PROGRESS" -> mergeLearningProgress(s1, s2);
            case "GOAL" -> mergeGoal(s1, s2);
            default -> mergeGeneric(s1, s2);
        };
    }

    // SKILL：递进式描述
    private String mergeSkill(String s1, String s2) {
        // 如果 s1 写"学习X基础"，s2 写"完成X高级优化"→ 合并递进
        return "已掌握基础：" + s1 + "；并进阶实践：" + s2;
    }

    // PROJECT：项目里程碑
    private String mergeProject(String s1, String s2) {
        return s1 + "；里程碑补充：" + s2;
    }

    // LEARNING_PROGRESS：优先保留更新进度百分比更高 / 内容更长的，
    // 如果两者都是百分比描述，取数字更高的
    private String mergeLearningProgress(String s1, String s2) {
        int p1 = extractPercent(s1);
        int p2 = extractPercent(s2);
        if (p2 > p1) {
            // s2 进度更高，s1 是前期里程碑 → 合并
            return "里程碑（" + p1 + "%→" + p2 + "%）：" + s1 + "；当前进展：" + s2;
        }
        if (p1 == p2 && p1 > 0) {
            return s1 + "；补充说明：" + s2;
        }
        // 没有百分比，就按进度顺序
        return "阶段1：" + s1 + "；阶段2：" + s2;
    }

    // GOAL：合并多个职业目标，取并集
    private String mergeGoal(String s1, String s2) {
        return s1 + "；同时兼顾：" + s2;
    }

    // 通用合并（EXPERIENCE / PREFERENCE / ACHIEVEMENT）
    private String mergeGeneric(String s1, String s2) {
        return s1 + "；补充：" + s2;
    }

    // ============================================================
    // 小工具
    // ============================================================

    private static String normalizeKey(String key) {
        if (key == null) return "";
        // 忽略大小写、前后空白、末尾的 "基础" / "进阶" / "高级" 等后缀用于判断同 key
        return key.trim().toLowerCase()
                .replaceAll("(基础|进阶|高级|入门|专项|课程)$", "")
                .replaceAll("\\s+", "");
    }

    private static boolean eq(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equalsIgnoreCase(b);
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    private static boolean fuzzyContains(String bigger, String smaller) {
        if (bigger == null || smaller == null) return false;
        String b = bigger.toLowerCase();
        String s = smaller.toLowerCase();
        // 只要核心 60% 的字符都出现就认为包含
        int match = 0;
        for (int i = 0; i < s.length(); i++) {
            if (b.indexOf(s.charAt(i)) >= 0) match++;
        }
        return match >= s.length() * 0.6;
    }

    private static String stripTrailingPunct(String s) {
        if (s == null) return "";
        int end = s.length();
        while (end > 0 && "。.;；，,！!？? \t".indexOf(s.charAt(end - 1)) >= 0) end--;
        return s.substring(0, end);
    }

    private static String defaultValueFor(String newValue) {
        return newValue == null ? "" : newValue;
    }

    private static int extractPercent(String s) {
        if (s == null) return 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,3})\\s*%").matcher(s);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (Exception ignore) {}
        }
        return 0;
    }

    private static double rounded(double d) {
        // 保留 2 位小数，避免 confidence 出现 0.9100000000001 这类怪值
        return Math.round(d * 100.0) / 100.0;
    }
}
