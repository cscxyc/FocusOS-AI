package com.focusos.agent;

import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Sprint 7-A: Resume Optimization Agent
 * <p>
 * 职责：根据目标 JD + 用户真实经历，基于 STAR 原则生成简历优化建议。
 * <p>
 * 输入：jobDescription + resumeContext + experienceContext + projectContext
 * 输出 JSON：
 * {
 *   "summaryOptimization": "",
 *   "experienceOptimization": [{"original":"", "optimized":"", "reason":""}],
 *   "projectOptimization": [{"original":"", "optimized":"", "reason":""}],
 *   "keywordsToAdd": []
 * }
 * <p>
 * 质量控制：
 * - 不编造用户经历
 * - 保留真实性（重新表达而非创造）
 * - 遵循 STAR 原则（Situation/Task/Action/Result）
 * - 突出技术难点、业务价值、工程能力
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeOptimizationAgent implements FocusAgent {

    private final ChatLanguageModel chatLanguageModel;
    private final AgentPromptProvider promptProvider;

    @Override
    public String type() {
        return "resume-optimization";
    }

    /**
     * 核心：基于 STAR 原则的简历优化
     *
     * @param jobDescription      目标 JD
     * @param resumeContext       简历上下文（来自 Personal RAG）
     * @param experienceContext   实习/工作经历上下文
     * @param projectContext      项目经历上下文
     * @return 结构化 JSON 优化建议
     */
    public String optimizeWithSTAR(String jobDescription, String resumeContext,
                                    String experienceContext, String projectContext) {
        boolean profileInsufficient = isBlank(resumeContext) && isBlank(experienceContext) && isBlank(projectContext);

        String prompt = String.format("""
                你是简历优化专家。请根据目标 JD 和用户真实经历，基于 STAR 原则生成简历优化建议。

                【目标职位 JD】
                %s

                【用户简历资料（来自 Personal RAG）】
                %s

                【用户实习/工作经历（来自 Personal RAG）】
                %s

                【用户项目经历（来自 Personal RAG）】
                %s

                %s

                严格按以下 JSON 格式输出（不要输出其他内容，不要使用 Markdown 代码块）：
                {
                  "summaryOptimization": "优化后的简历个人摘要（2-3句话，突出与目标 JD 的匹配点，基于真实经历）",
                  "experienceOptimization": [
                    {
                      "original": "用户原始经历描述（从资料中摘录）",
                      "optimized": "STAR 法则改写后的描述（Situation/Task/Action/Result 结构，突出技术难点+业务价值+工程能力）",
                      "reason": "优化原因（为什么这样改，体现了什么能力）"
                    }
                  ],
                  "projectOptimization": [
                    {
                      "original": "用户原始项目描述（从资料中摘录）",
                      "optimized": "STAR 法则改写后的项目描述（突出技术架构、难点突破、量化成果）",
                      "reason": "优化原因"
                    }
                  ],
                  "keywordsToAdd": ["建议添加的 ATS 关键词1", "关键词2"]
                }

                要求：
                1. experienceOptimization 至少 2 条，最多 5 条
                2. projectOptimization 至少 1 条，最多 4 条
                3. keywordsToAdd 至少 3 个，最多 8 个（基于 JD 和用户经历）
                4. 所有 original 必须来自用户真实资料，不能编造
                5. 所有 optimized 必须基于 original 重新表达，不能添加用户没做过的事
                6. 量化数据只能基于用户提供的真实信息推算
                """,
                isBlank(jobDescription) ? "（未提供具体 JD）" : jobDescription,
                isBlank(resumeContext) ? "（无简历资料）" : resumeContext,
                isBlank(experienceContext) ? "（无实习/工作经历）" : experienceContext,
                isBlank(projectContext) ? "（无项目经历）" : projectContext,
                profileInsufficient ? "【警告】用户资料严重不足，请在 summaryOptimization 中明确提示用户补充简历、项目、实习资料。" : "");

        String fullPrompt = promptProvider.resumeOptimizationSystemPrompt() + "\n\n" + prompt;

        try {
            String response = chatLanguageModel.chat(fullPrompt);
            return extractJson(response);
        } catch (Exception e) {
            log.error("Failed to optimize resume with STAR", e);
            return "{\"summaryOptimization\":\"简历优化失败，请稍后重试\",\"experienceOptimization\":[],\"projectOptimization\":[],\"keywordsToAdd\":[]}";
        }
    }

    @Override
    public String handle(String message, Long userId, String context) {
        // 从 context 中解析 WorkflowContext 传递的内容
        // WorkflowContext.renderAsPromptContext() 会输出【用户画像】等结构化内容
        // message 中包含任务目标 + 前置任务输出（CAREER_ANALYSIS 的结果）

        String resumeContext = extractSection(context, "简历/技能", "简历");
        String experienceContext = extractSection(context, "实习/工作经历", "经历");
        String projectContext = extractSection(context, "项目经历", "项目");

        // 从 message 或 context 中提取 JD
        String jobDescription = extractJD(message, context);

        log.info("ResumeOptimizationAgent.handle: userId={}, jdLength={}, resumeLen={}, expLen={}, projLen={}",
                userId, jobDescription.length(), resumeContext.length(), experienceContext.length(), projectContext.length());

        return optimizeWithSTAR(jobDescription, resumeContext, experienceContext, projectContext);
    }

    /**
     * 从文本中提取指定区块内容
     */
    private String extractSection(String text, String... sectionMarkers) {
        if (text == null || text.isBlank()) return "";
        for (String marker : sectionMarkers) {
            int idx = text.indexOf("【" + marker);
            if (idx >= 0) {
                int end = text.indexOf("【", idx + 1);
                if (end < 0) end = text.length();
                return text.substring(idx, end).trim();
            }
        }
        return "";
    }

    /**
     * 从 message 或 context 中提取 JD 内容
     */
    private String extractJD(String message, String context) {
        // 优先从 message 中的"输入参数"提取
        if (message != null) {
            int jdIdx = message.indexOf("职位描述");
            if (jdIdx < 0) jdIdx = message.indexOf("JD");
            if (jdIdx < 0) jdIdx = message.indexOf("岗位");
            if (jdIdx >= 0) {
                return message.substring(jdIdx).trim();
            }
        }
        // 从前置任务输出中提取（CAREER_ANALYSIS 结果中可能包含 JD）
        if (context != null && context.contains("职位描述")) {
            int idx = context.indexOf("职位描述");
            int end = context.indexOf("\n\n", idx);
            if (end < 0) end = context.length();
            return context.substring(idx, end).trim();
        }
        // 退化：使用完整 message
        return message != null ? message : "";
    }

    /**
     * 从 LLM 响应中提取 JSON（去除可能的 Markdown 包裹）
     */
    private String extractJson(String response) {
        if (response == null) return "{}";
        String trimmed = response.trim();
        // 去除 ```json ... ``` 包裹
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf("{");
            int end = trimmed.lastIndexOf("}");
            if (start >= 0 && end > start) {
                return trimmed.substring(start, end + 1);
            }
        }
        // 直接查找 JSON 边界
        int start = trimmed.indexOf("{");
        int end = trimmed.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
