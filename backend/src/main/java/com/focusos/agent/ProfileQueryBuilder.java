package com.focusos.agent;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Sprint 6-C: Personal RAG Profile Query 构建器
 * <p>
 * 解决问题：
 * 1. 之前 searchUserProfile 使用"技能 技术 工具 框架 编程语言"等通用关键词 embedding，
 *    与用户实际文档内容语义匹配度低，cosine 相似度经常低于 0.5 阈值
 * 2. 直接 embedding 完整用户目标（如"帮我规划AI应用开发转型路线"）会偏离个人资料检索意图
 * <p>
 * 策略：
 * - 为每个 category 生成短而精准的 query（5-15 字符），贴近文档实际用语
 * - 同时生成多个候选 query，提高召回率
 * - 检索时使用较低 minScore（0.3），靠 metadata 过滤保证用户隔离
 */
@Component
public class ProfileQueryBuilder {

    /**
     * 按分类生成检索 query 列表
     * <p>
     * 每个 category 生成 2-3 个短 query，分别从不同角度检索：
     * - 概述性 query：匹配文档标题/摘要
     * - 细节性 query：匹配文档正文关键词
     */
    public Map<String, List<String>> buildProfileQueries(List<String> categories) {
        return Map.of(
                "career", List.of(
                        "技能 技术栈 编程语言 框架",
                        "求职意向 职业方向 自我评价",
                        "教育背景 专业 学历"
                ),
                "project", List.of(
                        "项目名称 项目描述 技术架构",
                        "项目职责 功能模块 实现",
                        "项目成果 亮点 难点"
                ),
                "experience", List.of(
                        "实习经历 工作经历 公司",
                        "岗位职责 日常工作 团队",
                        "实习成果 收获 成长"
                ),
                "learning", List.of(
                        "学习笔记 知识点 课程",
                        "学习计划 学习方法 复习"
                ),
                "goal", List.of(
                        "职业目标 发展规划 方向",
                        "求职目标 岗位意向 期望"
                )
        );
    }

    /**
     * 为特定分类生成精简检索 query（单条，用于快速检索）
     */
    public String buildQueryForCategory(String category) {
        return switch (category) {
            case "career" -> "技能 技术栈 编程语言 框架 求职意向";
            case "project" -> "项目经历 技术架构 职责 成果";
            case "experience" -> "实习经历 工作经历 公司 岗位";
            case "learning" -> "学习笔记 知识点 课程";
            case "goal" -> "职业目标 发展规划 方向";
            default -> category;
        };
    }

    /**
     * 根据 Workflow 用户目标生成定向检索 query
     * <p>
     * 例如用户目标"帮我规划AI应用开发转型路线"，
     * 生成"AI 应用开发 Agent RAG 大模型"等技术向 query
     * 用于检索与目标相关的个人资料（如已有的 AI 项目经历）
     */
    public String buildGoalOrientedQuery(String userGoal) {
        if (userGoal == null || userGoal.isBlank()) {
            return "技能 项目 经历";
        }
        // 提取目标中的关键技术词，构建短 query
        StringBuilder sb = new StringBuilder();
        String[] techKeywords = {"AI", "人工智能", "大模型", "LLM", "Agent", "RAG",
                "Java", "Spring", "Python", "前端", "后端", "全栈",
                "转型", "学习", "规划", "开发", "应用"};
        for (String kw : techKeywords) {
            if (userGoal.contains(kw)) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(kw);
            }
        }
        // 兜底：如果没匹配到关键词，使用通用 query
        if (sb.isEmpty()) {
            return "技能 项目 经历 技术";
        }
        return sb.toString();
    }
}
