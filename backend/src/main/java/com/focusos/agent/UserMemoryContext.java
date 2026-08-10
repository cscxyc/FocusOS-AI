package com.focusos.agent;

import com.focusos.entity.UserMemory;
import lombok.Builder;
import lombok.Data;

import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Sprint 8-C: 用户长期记忆上下文（结构化 + 可直接注入 Agent prompt）
 * <p>
 * 与 WorkflowContext / UserProfileContext 的关系：
 * - WorkflowContext：整个 Workflow 级别的共享上下文，包含 userProfileContext + memoryContext + careerContext
 * - UserProfileContext：Personal RAG 检索到的静态资料（简历/项目文档原文）
 * - UserMemoryContext：动态沉淀的长期成长状态（技能熟练度、项目完成、学习进度、目标变化）
 * <p>
 * Agent 接入优先级：Memory > Profile > Resume
 */
@Data
@Builder
public class UserMemoryContext {

    private Long userId;
    /** 构建此上下文时使用的最小 confidence 阈值（默认 0.5，过滤低置信噪声） */
    private double minConfidence;

    // 按类型分组后的结构化记忆
    @Builder.Default
    private List<MemoryItem> skills = new ArrayList<>();
    @Builder.Default
    private List<MemoryItem> projects = new ArrayList<>();
    @Builder.Default
    private List<MemoryItem> experiences = new ArrayList<>();
    @Builder.Default
    private List<MemoryItem> goals = new ArrayList<>();
    @Builder.Default
    private List<MemoryItem> learningProgresses = new ArrayList<>();
    @Builder.Default
    private List<MemoryItem> preferences = new ArrayList<>();
    @Builder.Default
    private List<MemoryItem> achievements = new ArrayList<>();

    /** 构建是否成功 */
    private boolean retrievalSuccess;
    /** 构建失败原因 */
    private String retrievalError;
    /** 原始记忆总条数（用于统计） */
    private int totalMemories;

    /**
     * 单项记忆条目（和 UserMemory 字段对应，但更轻量）
     */
    @Data
    @Builder
    public static class MemoryItem {
        private String key;
        private String value;
        private String type;
        private Double confidence;
        private String source;
        private String updatedAt;
    }

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // ============================================================
    // 构建方法
    // ============================================================

    /**
     * 从 UserMemory 实体列表构建结构化上下文
     */
    public static UserMemoryContext fromMemories(Long userId, List<UserMemory> memories, double minConfidence) {
        UserMemoryContextBuilder b = builder()
                .userId(userId)
                .minConfidence(minConfidence)
                .retrievalSuccess(true)
                .totalMemories(memories == null ? 0 : memories.size());

        if (memories == null) memories = List.of();
        List<MemoryItem> skills = new ArrayList<>();
        List<MemoryItem> projects = new ArrayList<>();
        List<MemoryItem> experiences = new ArrayList<>();
        List<MemoryItem> goals = new ArrayList<>();
        List<MemoryItem> learning = new ArrayList<>();
        List<MemoryItem> prefs = new ArrayList<>();
        List<MemoryItem> achievements = new ArrayList<>();

        for (UserMemory m : memories) {
            MemoryItem item = MemoryItem.builder()
                    .key(m.getMemoryKey())
                    .value(m.getMemoryValue())
                    .type(m.getMemoryType())
                    .confidence(m.getConfidence())
                    .source(m.getSource())
                    .updatedAt(m.getUpdatedAt() == null ? null : m.getUpdatedAt().format(DTF))
                    .build();
            switch (m.getMemoryType() == null ? "SKILL" : m.getMemoryType().toUpperCase()) {
                case "SKILL" -> skills.add(item);
                case "PROJECT" -> projects.add(item);
                case "EXPERIENCE" -> experiences.add(item);
                case "GOAL" -> goals.add(item);
                case "LEARNING_PROGRESS" -> learning.add(item);
                case "PREFERENCE" -> prefs.add(item);
                case "ACHIEVEMENT" -> achievements.add(item);
                default -> skills.add(item);
            }
        }
        // confidence 降序（高置信优先展示，Agent prompt 也更容易读到重点）
        skills.sort(byConfDesc);
        projects.sort(byConfDesc);
        experiences.sort(byConfDesc);
        goals.sort(byConfDesc);
        learning.sort(byConfDesc);
        prefs.sort(byConfDesc);
        achievements.sort(byConfDesc);

        return b.skills(skills).projects(projects).experiences(experiences)
                .goals(goals).learningProgresses(learning).preferences(prefs)
                .achievements(achievements).build();
    }

    public static UserMemoryContext empty(String error) {
        return builder().retrievalSuccess(false).retrievalError(error)
                .minConfidence(0.5).totalMemories(0)
                .skills(List.of()).projects(List.of()).experiences(List.of())
                .goals(List.of()).learningProgresses(List.of()).preferences(List.of())
                .achievements(List.of()).build();
    }

    private static final Comparator<MemoryItem> byConfDesc = (a, b) -> {
        double ca = a.getConfidence() == null ? 0.5 : a.getConfidence();
        double cb = b.getConfidence() == null ? 0.5 : b.getConfidence();
        return Double.compare(cb, ca);
    };

    // ============================================================
    // 注入 Agent prompt：renderAsPromptSection
    // ============================================================

    /**
     * 渲染为注入 Agent prompt 的文本块。
     * <p>
     * 输出示例：
     * 【长期成长记忆（Personal Memory，仅供真实性核查与动态调整）】
     * 已掌握技能（confidence ≥0.5，高置信优先）：
     * - Java (0.92)：完成Spring Boot企业项目开发...（来源：PROJECT_COMPLETED_EVENT）
     * - RAG (0.85)：RAG系统开发，实现WorkflowContext共享...（来源：CAREER_GROWTH_TASK_COMPLETED）
     * 已完成项目：
     * - FocusOS AI 微服务化 (0.95)：Nacos+Gateway+OpenFeign+Kafka
     * 学习进度：
     * - Spring Cloud 微服务课程 (0.85)：进度60%，已完成Nacos/Gateway
     * 当前职业目标：
     * - AI应用开发工程师 (0.98)：入职字节跳动AI应用开发岗位
     */
    public String renderAsPromptSection() {
        StringBuilder sb = new StringBuilder();
        sb.append("【长期成长记忆（Personal Memory，仅供真实性核查与动态调整，优先级高于简历/RAG静态资料）】\n");

        if (!retrievalSuccess) {
            sb.append("（长期记忆加载失败：").append(retrievalError == null ? "未知原因" : retrievalError).append("，将仅使用简历与 Personal RAG 静态资料）\n");
            return sb.toString();
        }

        if (totalMemories == 0) {
            sb.append("（暂无长期记忆数据。长期记忆将在用户完成学习/项目/面试后自动沉淀，用于动态调整职业成长规划、面试深挖方向、简历评分加分。）\n");
            return sb.toString();
        }

        appendList(sb, "已掌握技能", skills);
        appendList(sb, "已完成项目", projects);
        appendList(sb, "学习进度跟踪", learningProgresses);
        appendList(sb, "经验沉淀", experiences);
        appendList(sb, "当前职业目标", goals);
        appendList(sb, "个人偏好", preferences);
        appendList(sb, "成就与证书", achievements);
        return sb.toString();
    }

    private void appendList(StringBuilder sb, String label, List<MemoryItem> list) {
        if (list == null || list.isEmpty()) return;
        sb.append(label).append("（共 ").append(list.size()).append(" 条，confidence 高→低）：\n");
        for (MemoryItem item : list) {
            String confStr = item.getConfidence() == null ? "0.50" : String.format("%.2f", item.getConfidence());
            String srcStr = item.getSource() == null || item.getSource().isBlank() ? "" : "（来源：" + item.getSource() + "）";
            String timeStr = item.getUpdatedAt() == null ? "" : " [更新于 " + item.getUpdatedAt() + "]";
            sb.append("- ").append(item.getKey()).append(" (conf=").append(confStr).append(")：")
                    .append(limitLen(item.getValue(), 150))
                    .append(srcStr).append(timeStr).append("\n");
        }
        sb.append("\n");
    }

    /**
     * 渲染为「已掌握技能模糊命中清单」—— 供 CareerGrowthAgent 快速判断：
     * 若 Gap skill 在此清单中出现（模糊匹配），则不要安排基础学习，改为进阶内容。
     * <p>
     * 返回形如："[Milvus(0.92), RAG(0.88), Spring Boot(0.95)]"
     */
    public String renderSkillHintsForCareerGrowth() {
        if (skills == null || skills.isEmpty()) {
            return "（用户尚未沉淀任何技能掌握记忆，按常规成长路径安排即可）";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【特别约束：用户已沉淀如下技能掌握状态（请在生成 skillGaps / roadmap / weeklyTasks 时严格遵守）】\n");
        sb.append("若 Gap 技能名称与下列技能名有重叠（如用户已掌握 Milvus confidence≥0.9）：\n");
        sb.append("  ❌ 禁止安排：学习 Milvus 基础 / Milvus 入门 / Milvus 概念介绍 这类基础内容\n");
        sb.append("  ✅ 改为安排：Milvus 高并发优化 / Milvus 集群高可用 / Milvus 冷热数据分层 / Milvus 与 Redis 缓存协同 这类进阶内容\n\n");
        sb.append("用户已掌握技能清单：\n");
        for (MemoryItem s : skills) {
            if (s.getConfidence() == null || s.getConfidence() < 0.7) continue; // 仅高置信作为约束
            String confStr = String.format("%.2f", s.getConfidence());
            sb.append("- 技能「").append(s.getKey()).append("」(confidence=").append(confStr)
                    .append(")：当前状态 → ").append(limitLen(s.getValue(), 80)).append("\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    /**
     * 渲染为「项目深挖优先级清单」—— 供 InterviewAgent 提升项目深挖问题的准确度：
     * 项目深挖优先级 Memory(有状态/有沉淀) > Resume(静态描述) > Personal RAG（原文片段）
     */
    public String renderProjectPrioritiesForInterview() {
        StringBuilder sb = new StringBuilder();
        sb.append("【特别约束：项目深挖优先级 Memory > Resume > RAG 原文。请优先围绕下列有明确沉淀的项目设计深挖问题】\n");
        if (projects != null && !projects.isEmpty()) {
            sb.append("【Memory 中已明确沉淀完成的项目（请深挖具体决策、问题、成果，避免宽泛问题）】\n");
            for (MemoryItem p : projects) {
                String confStr = p.getConfidence() == null ? "0.50" : String.format("%.2f", p.getConfidence());
                sb.append("- 项目「").append(p.getKey()).append("」(conf=").append(confStr)
                        .append(")：").append(limitLen(p.getValue(), 120)).append("\n");
                sb.append("  深挖设计示例：请结合").append(p.getKey()).append("项目，介绍你如何设计XXX链路？为什么选XXX而不是YYY？遇到了什么问题？如何解决？\n");
            }
            sb.append("\n");
        } else {
            sb.append("（Memory 中尚无完成项目，按 Interview 默认规则从 Resume / RAG 提取即可）\n\n");
        }

        if (skills != null && !skills.isEmpty()) {
            sb.append("【Memory 中已明确沉淀掌握的技能（请围绕技术决策设计深度问题，而非问「什么是XX」类基础题）】\n");
            for (MemoryItem s : skills) {
                if (s.getConfidence() == null || s.getConfidence() < 0.8) continue;
                sb.append("- ").append(s.getKey()).append("（建议深挖：技术选型权衡、踩坑经历、性能调优数据）\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private static String limitLen(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max) + "...";
    }

    /**
     * 给定 skillName，查找 memory 中是否已高置信掌握（confidence >= 0.8 + 模糊 key 匹配）
     * 用于 ResumeEvaluator growthAlignmentScore 判断：
     * 简历中写了 RAG / Milvus，而 Memory 中也有 Milvus 优化经验 → 成长一致性加分
     */
    public Optional<MemoryItem> findMasteredSkill(String keyword) {
        if (keyword == null || keyword.isBlank() || skills == null) return Optional.empty();
        String k = keyword.toLowerCase().trim();
        MemoryItem best = null;
        double bestConf = 0;
        for (MemoryItem s : skills) {
            if (s.getConfidence() != null && s.getConfidence() < 0.6) continue;
            String key = s.getKey() == null ? "" : s.getKey().toLowerCase();
            String val = s.getValue() == null ? "" : s.getValue().toLowerCase();
            if ((!key.isEmpty() && key.contains(k)) || val.contains(k)) {
                double c = s.getConfidence() == null ? 0.5 : s.getConfidence();
                if (c > bestConf) {
                    bestConf = c;
                    best = s;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * 是否存在某个完成项目（模糊匹配项目名）
     */
    public boolean hasCompletedProject(String keyword) {
        if (keyword == null || keyword.isBlank() || projects == null) return false;
        String k = keyword.toLowerCase().trim();
        for (MemoryItem p : projects) {
            String name = p.getKey() == null ? "" : p.getKey().toLowerCase();
            String desc = p.getValue() == null ? "" : p.getValue().toLowerCase();
            if (name.contains(k) || desc.contains(k)) return true;
        }
        return false;
    }
}
