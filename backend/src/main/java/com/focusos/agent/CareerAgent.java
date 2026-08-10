package com.focusos.agent;

import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class CareerAgent implements FocusAgent {

    private final ChatLanguageModel chatLanguageModel;
    private final AgentPromptProvider promptProvider;
    private final RAGAgent ragAgent;

    // 使用 @Lazy 避免循环依赖（RAGAgent 不依赖 CareerAgent，但保险起见）
    public CareerAgent(ChatLanguageModel chatLanguageModel, AgentPromptProvider promptProvider,
                       @Lazy RAGAgent ragAgent) {
        this.chatLanguageModel = chatLanguageModel;
        this.promptProvider = promptProvider;
        this.ragAgent = ragAgent;
    }

    /**
     * Sprint 5-B: Personal RAG 增强的 JD 分析
     *
     * 流程：JD → CareerAgent → Personal RAG → 用户画像 → 岗位匹配
     *
     * 检索用户的简历、项目经历、实习经历，构建用户画像后与 JD 匹配。
     * 输出结构化 JSON：matchScore / advantages / gaps / resumeSuggestions / learningPlan / interviewQuestions
     */
    public String analyzeJobDescription(String jobDescription, String skills, String experience, Long userId) {
        // 1. 检索用户个人知识库（简历 + 项目 + 实习）
        String userProfile = "";
        if (userId != null) {
            userProfile = ragAgent.searchUserProfile(userId, List.of("career", "project", "experience"));
            log.info("Personal RAG: retrieved user profile for userId={} ({} chars)", userId, userProfile.length());
        }

        // 2. 构造 prompt，包含 JD + CareerProfile + Personal RAG 检索结果
        String prompt = String.format("""
                你是一位专业的职业顾问。请分析以下职位描述，并结合用户个人知识库进行匹配分析。

                【职位描述】
                %s

                【用户职业档案】
                技能：%s
                经验：%s

                【用户个人知识库（RAG 检索结果）】
                %s

                请基于以上信息，进行深度匹配分析，并严格按以下 JSON 格式输出（不要输出其他内容）：

                {
                  "matchScore": <0-100 的整数，表示整体匹配度>,
                  "candidateProfile": {
                    "summary": "用户画像摘要（基于个人知识库的真实资料）",
                    "coreSkills": ["核心技能1", "核心技能2"],
                    "experience": "工作/实习经历摘要"
                  },
                  "advantages": ["优势1", "优势2", "优势3"],
                  "gaps": ["差距1", "差距2", "差距3"],
                  "resumeSuggestions": ["简历修改建议1", "简历修改建议2"],
                  "learningPlan": ["学习计划1", "学习计划2"],
                  "interviewQuestions": ["面试题1", "面试题2", "面试题3"]
                }

                要求：
                - matchScore 基于技能匹配度、经验相关性、项目经历契合度综合评估
                - candidateProfile 必须基于用户个人知识库中的真实资料生成，不能编造
                - advantages 和 gaps 必须具体引用用户知识库中的实际经历
                - resumeSuggestions 针对该 JD 提出可操作的简历修改方向
                - learningPlan 针对差距提出具体学习路径
                - interviewQuestions 基于该岗位高频面试考点生成
                - 所有内容用中文，专业、具体、可操作
                """,
                jobDescription,
                skills != null ? skills : "未提供",
                experience != null ? experience : "未提供",
                userProfile.isEmpty() ? "（用户尚未上传个人知识库文档）" : userProfile);

        String fullPrompt = promptProvider.careerSystemPrompt() + "\n\n" + prompt;

        try {
            return chatLanguageModel.chat(fullPrompt);
        } catch (Exception e) {
            log.error("Failed to analyze JD with Personal RAG", e);
            return "职位分析失败，请稍后重试。";
        }
    }

    /**
     * 向后兼容：不带 userId 的 JD 分析（不检索 Personal RAG）
     */
    public String analyzeJobDescription(String jobDescription, String skills, String experience) {
        return analyzeJobDescription(jobDescription, skills, experience, null);
    }

    /**
     * Sprint 7-A: Career Agent 2.0 — 结构化 JSON 输出 + WorkflowContext 集成
     * <p>
     * 在 Career Workflow 的 CAREER_ANALYSIS 任务中被调用，接收已渲染的 WorkflowContext
     * （包含 Personal RAG 检索的带来源用户画像），输出标准 JSON 格式。
     * <p>
     * 输出 JSON 格式：
     * {
     *   "matchScore": 85,
     *   "candidateProfile": "",
     *   "advantages": [],
     *   "gaps": [],
     *   "jobRequirements": [],
     *   "suggestions": [],
     *   "interviewFocus": []
     * }
     *
     * @param jobDescription     目标 JD
     * @param workflowContextText WorkflowContext.renderAsPromptContext() 渲染的文本（含来源）
     * @return 结构化 JSON 字符串
     */
    public String analyzeCareerStructured(String jobDescription, String workflowContextText) {
        boolean profileEmpty = workflowContextText == null
                || workflowContextText.isBlank()
                || workflowContextText.contains("RAG 检索失败")
                || workflowContextText.contains("降级为通用上下文");

        String prompt = String.format("""
                你是 Career Agent 2.0。请基于以下信息进行岗位匹配分析，并严格输出 JSON。

                【目标职位 JD】
                %s

                【用户画像（来自 Personal RAG，含来源标注）】
                %s

                %s

                严格按以下 JSON 格式输出（不要输出任何其他内容，不要使用 Markdown 代码块）：
                {
                  "matchScore": <0-100 整数，综合匹配度>,
                  "candidateProfile": "用户画像摘要（基于个人知识库真实资料，2-3句话）",
                  "advantages": [
                    "优势1（必须引用具体项目/实习/技能，并标注来源，如：基于 FocusOS AI 项目的 RAG 实现经验）",
                    "优势2",
                    "优势3"
                  ],
                  "gaps": [
                    "差距1（JD 要求但用户不具备的能力）",
                    "差距2"
                  ],
                  "jobRequirements": [
                    "JD 要求1",
                    "JD 要求2",
                    "JD 要求3"
                  ],
                  "suggestions": [
                    "建议1（针对差距的具体行动建议）",
                    "建议2"
                  ],
                  "interviewFocus": [
                    "面试重点1（基于 JD 高频考点）",
                    "面试重点2"
                  ]
                }

                要求：
                1. matchScore 基于技能匹配度、经验相关性、项目契合度综合评估
                2. advantages 至少 2 条，最多 5 条，每条必须引用用户真实经历
                3. gaps 至少 1 条，最多 4 条
                4. jobRequirements 至少 3 条，从 JD 中提取核心要求
                5. suggestions 至少 2 条，针对差距提出可执行建议
                6. interviewFocus 至少 2 条，基于岗位高频面试考点
                7. 如果用户资料不足，matchScore 不超过 40，并在 candidateProfile 中明确提示
                """,
                jobDescription != null ? jobDescription : "（未提供 JD）",
                profileEmpty ? "（用户资料不足，RAG 检索为空或失败）" : workflowContextText,
                profileEmpty ? "【警告】用户资料不足，matchScore 不应超过 40，并在 candidateProfile 中提示用户补充简历/项目/实习资料。" : "");

        String fullPrompt = promptProvider.careerSystemPrompt() + "\n\n" + prompt;

        try {
            String response = chatLanguageModel.chat(fullPrompt);
            return extractJsonObject(response);
        } catch (Exception e) {
            log.error("Failed to analyze career structured", e);
            return "{\"matchScore\":0,\"candidateProfile\":\"分析失败\",\"advantages\":[],\"gaps\":[],\"jobRequirements\":[],\"suggestions\":[],\"interviewFocus\":[]}";
        }
    }

    /**
     * Sprint 7-A: 面试准备生成（INTERVIEW_PREPARATION 任务调用）
     * 基于 Career 分析结果 + 用户经历，生成面试准备题和答题要点
     */
    public String generateInterviewPreparation(String jobDescription, String workflowContextText,
                                                String careerAnalysisResult) {
        String prompt = String.format("""
                你是面试准备专家。请基于目标 JD、用户真实经历和岗位匹配分析结果，生成面试准备方案。

                【目标职位 JD】
                %s

                【用户画像（Personal RAG）】
                %s

                【岗位匹配分析结果】
                %s

                严格按以下 JSON 格式输出（不要输出其他内容，不要 Markdown 代码块）：
                {
                  "technicalQuestions": [
                    {"question":"技术面试题","keyPoints":["答题要点1","要点2"],"relatedExperience":"用户可引用的经历"}
                  ],
                  "behavioralQuestions": [
                    {"question":"行为面试题","starFramework":"STAR 框架答题建议"}
                  ],
                  "projectDeepDive": [
                    {"project":"用户项目名","likelyQuestions":["深挖问题1"],"preparationTips":"准备建议"}
                  ],
                  "selfIntroduction": "基于用户经历的自我介绍模板（2分钟版）",
                  "weaknessResponses": [
                    {"weakness":"面试官可能质疑的弱点","response":"应对话术"}
                  ]
                }

                要求：所有内容基于用户真实经历，不编造。至少 3 个技术题、2 个行为题、2 个项目深挖。
                """,
                jobDescription != null ? jobDescription : "（未提供）",
                workflowContextText != null ? workflowContextText : "（无）",
                careerAnalysisResult != null ? careerAnalysisResult : "（无）");

        String fullPrompt = promptProvider.careerSystemPrompt() + "\n\n" + prompt;

        try {
            return extractJsonObject(chatLanguageModel.chat(fullPrompt));
        } catch (Exception e) {
            log.error("Failed to generate interview preparation", e);
            return "{\"technicalQuestions\":[],\"behavioralQuestions\":[],\"projectDeepDive\":[],\"selfIntroduction\":\"\",\"weaknessResponses\":[]}";
        }
    }

    /**
     * Sprint 7-A: 技能差距分析（SKILL_GAP_ANALYSIS 任务调用）
     * 对比 JD 要求和用户实际技能，输出结构化差距分析
     */
    public String analyzeSkillGap(String jobDescription, String workflowContextText) {
        String prompt = String.format("""
                你是技能差距分析专家。请对比目标 JD 的要求和用户实际技能，生成结构化差距分析。

                【目标职位 JD】
                %s

                【用户实际技能与经历（Personal RAG）】
                %s

                严格按以下 JSON 格式输出（不要输出其他内容，不要 Markdown 代码块）：
                {
                  "matchedSkills": [
                    {"skill":"技能名","evidence":"用户具备该技能的证据（来源）","proficiency":"初级/中级/高级"}
                  ],
                  "missingSkills": [
                    {"skill":"缺失技能","importance":"核心/重要/加分","learnDifficulty":"easy/medium/hard"}
                  ],
                  "partialSkills": [
                    {"skill":"部分匹配技能","currentLevel":"当前水平","requiredLevel":"要求水平","gap":"具体差距"}
                  ],
                  "overallAssessment": "整体技能差距评估（1-2句话）",
                  "priorityLearning": [
                    {"skill":"优先学习技能","reason":"原因","estimatedTime":"预计学习时间"}
                  ]
                }

                要求：matchedSkills 至少 2 条，missingSkills 至少 1 条。所有 matched 必须有 evidence 来源。
                """,
                jobDescription != null ? jobDescription : "（未提供）",
                workflowContextText != null ? workflowContextText : "（无用户资料）");

        String fullPrompt = promptProvider.careerSystemPrompt() + "\n\n" + prompt;

        try {
            return extractJsonObject(chatLanguageModel.chat(fullPrompt));
        } catch (Exception e) {
            log.error("Failed to analyze skill gap", e);
            return "{\"matchedSkills\":[],\"missingSkills\":[],\"partialSkills\":[],\"overallAssessment\":\"分析失败\",\"priorityLearning\":[]}";
        }
    }

    /**
     * 从 LLM 响应中提取 JSON 对象（去除 Markdown 包裹）
     */
    private String extractJsonObject(String response) {
        if (response == null) return "{}";
        String trimmed = response.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf("{");
            int end = trimmed.lastIndexOf("}");
            if (start >= 0 && end > start) {
                return trimmed.substring(start, end + 1);
            }
        }
        int start = trimmed.indexOf("{");
        int end = trimmed.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    /**
     * Sprint 5-B: 基于个人知识库的职业方向推荐
     */
    public String recommendCareerDirections(Long userId) {
        String userProfile = "";
        if (userId != null) {
            userProfile = ragAgent.searchUserProfile(userId, List.of("career", "project", "experience", "goal", "learning"));
            log.info("Personal RAG: retrieved full profile for career recommendation, userId={} ({} chars)", userId, userProfile.length());
        }

        String prompt = String.format("""
                你是一位专业的职业顾问。根据用户的个人知识库，推荐适合的职业方向。

                【用户个人知识库】
                %s

                请基于用户的技能、项目经历、实习经历、学习笔记和职业目标，推荐 3-5 个最适合的岗位方向。

                严格按以下 JSON 格式输出（不要输出其他内容）：
                {
                  "recommendations": [
                    {
                      "position": "岗位名称",
                      "matchScore": <0-100>,
                      "reasons": ["匹配原因1", "匹配原因2"],
                      "gaps": ["需提升的技能1"],
                      "suggestions": "具体发展建议"
                    }
                  ]
                }

                要求：所有内容用中文，基于用户真实经历，具体、可操作。
                """, userProfile.isEmpty() ? "（用户尚未上传个人知识库文档）" : userProfile);

        String fullPrompt = promptProvider.careerSystemPrompt() + "\n\n" + prompt;

        try {
            return chatLanguageModel.chat(fullPrompt);
        } catch (Exception e) {
            log.error("Failed to recommend career directions", e);
            return "职业方向推荐失败，请稍后重试。";
        }
    }

    /**
     * Sprint 5-B: 基于个人知识库的简历优化建议
     */
    public String optimizeResumeWithProfile(String jobDescription, Long userId) {
        String userProfile = "";
        if (userId != null) {
            userProfile = ragAgent.searchUserProfile(userId, List.of("career", "project", "experience"));
        }

        String prompt = String.format("""
                你是一位简历优化专家。请根据目标职位描述和用户的个人知识库，提供简历修改建议。

                【目标职位描述】
                %s

                【用户个人知识库】
                %s

                请基于用户真实经历，提供具体的简历优化建议。

                严格按以下 JSON 格式输出（不要输出其他内容）：
                {
                  "summaryOptimization": "优化后的简历摘要",
                  "skillsHighlight": ["应突出的技能1", "应突出的技能2"],
                  "experienceRewrite": [
                    {
                      "original": "原始经历描述",
                      "optimized": "STAR法则改写后的描述"
                    }
                  ],
                  "atsKeywords": ["ATS关键词1", "ATS关键词2"],
                  "structureAdvice": "整体结构调整建议"
                }

                要求：所有内容用中文，基于用户真实经历，可直接使用。
                """,
                jobDescription != null ? jobDescription : "（未提供具体JD）",
                userProfile.isEmpty() ? "（用户尚未上传个人知识库文档）" : userProfile);

        String fullPrompt = promptProvider.careerSystemPrompt() + "\n\n" + prompt;

        try {
            return chatLanguageModel.chat(fullPrompt);
        } catch (Exception e) {
            log.error("Failed to optimize resume with profile", e);
            return "简历优化失败，请稍后重试。";
        }
    }

    public String optimizeResume(String summary, String skills, String experience, String jobDescription) {
        String prompt = String.format("""
                你是一位简历优化专家。请根据目标职位描述，优化用户的简历内容：

                【目标职位描述】
                %s

                【用户简介】
                %s

                【用户技能】
                %s

                【用户经验】
                %s

                请提供：
                1. 优化后的简历摘要
                2. 技能部分的优化表述
                3. 工作经验的STAR法则改写建议
                4. 关键词优化（针对ATS系统）
                5. 整体结构调整建议

                请用中文回答，输出可直接使用的优化内容。
                """, jobDescription, summary != null ? summary : "未提供",
                skills != null ? skills : "未提供", experience != null ? experience : "未提供");

        String fullPrompt = promptProvider.careerSystemPrompt() + "\n\n" + prompt;

        try {
            return chatLanguageModel.chat(fullPrompt);
        } catch (Exception e) {
            log.error("Failed to optimize resume", e);
            return "简历优化失败，请稍后重试。";
        }
    }

    public String generateCoverLetter(String jobDescription, String skills, String company) {
        String prompt = String.format("""
                你是一位求职信撰写专家。请根据以下信息生成一封专业的求职信：

                【目标职位描述】
                %s

                【用户技能背景】
                %s

                【目标公司】
                %s

                请生成一封完整的中文求职信，包括：
                1. 开头：表明申请意向
                2. 主体：展示相关技能和经验
                3. 结尾：表达热情和期待

                要求：专业、真诚、有说服力。
                """, jobDescription, skills != null ? skills : "未提供",
                company != null ? company : "贵公司");

        String fullPrompt = promptProvider.careerSystemPrompt() + "\n\n" + prompt;

        try {
            return chatLanguageModel.chat(fullPrompt);
        } catch (Exception e) {
            log.error("Failed to generate cover letter", e);
            return "求职信生成失败，请稍后重试。";
        }
    }

    @Override
    public String type() {
        return "career";
    }

    @Override
    public String handle(String message, Long userId, String context) {
        // Sprint 7-A: 根据 Workflow 任务类型路由到结构化输出方法
        // context 格式: "工作流任务类型: XXX\n输入参数: ...\n\n【用户原始目标】..."
        if (context != null) {
            String taskType = extractTaskType(context);
            String workflowContextText = extractWorkflowContext(context);
            String jd = extractJDFromMessage(message);

            if ("CAREER_ANALYSIS".equals(taskType)) {
                log.info("CareerAgent.handle → CAREER_ANALYSIS (structured), userId={}", userId);
                return analyzeCareerStructured(jd, workflowContextText);
            }
            if ("SKILL_GAP_ANALYSIS".equals(taskType)) {
                log.info("CareerAgent.handle → SKILL_GAP_ANALYSIS, userId={}", userId);
                return analyzeSkillGap(jd, workflowContextText);
            }
            if ("INTERVIEW_PREPARATION".equals(taskType)) {
                log.info("CareerAgent.handle → INTERVIEW_PREPARATION, userId={}", userId);
                String careerAnalysis = extractPreviousResult(message);
                return generateInterviewPreparation(jd, workflowContextText, careerAnalysis);
            }
        }

        // 通用对话：检索用户知识库
        String userProfile = "";
        if (userId != null) {
            userProfile = ragAgent.searchUserProfile(userId, List.of("career", "project", "experience"));
        }

        String prompt = String.format("""
                你是一位专业的职业顾问和求职助手。

                用户个人知识库：
                %s

                对话上下文：
                %s

                用户问题：
                %s

                请基于用户个人知识库和问题，提供职业发展建议、JD分析、简历优化或面试准备指导。
                请用中文回答，专业、具体、可操作。
                """,
                userProfile.isEmpty() ? "（暂无个人知识库数据）" : userProfile,
                context != null ? context : "无", message);

        String fullPrompt = promptProvider.careerSystemPrompt() + "\n\n" + prompt;

        try {
            return chatLanguageModel.chat(fullPrompt);
        } catch (Exception e) {
            log.error("Failed to handle career agent request", e);
            return "职业助手暂时无法回答，请稍后重试。";
        }
    }

    /**
     * 从 context 中提取任务类型
     */
    private String extractTaskType(String context) {
        if (context == null) return "";
        int idx = context.indexOf("工作流任务类型:");
        if (idx < 0) idx = context.indexOf("工作流任务类型：");
        if (idx >= 0) {
            int end = context.indexOf("\n", idx);
            if (end > idx) {
                return context.substring(idx + "工作流任务类型:".length(), end).trim();
            }
        }
        return "";
    }

    /**
     * 从 context 中提取 WorkflowContext 渲染文本（【用户原始目标】之后的部分）
     */
    private String extractWorkflowContext(String context) {
        if (context == null) return "";
        int idx = context.indexOf("【用户原始目标】");
        if (idx >= 0) {
            return context.substring(idx);
        }
        return context;
    }

    /**
     * 从 message 中提取 JD（输入参数中的职位描述）
     */
    private String extractJDFromMessage(String message) {
        if (message == null) return "";
        // 输入参数: {"jobDescription":"..."} 或 输入参数: 职位描述:...
        int paramIdx = message.indexOf("输入参数:");
        if (paramIdx < 0) paramIdx = message.indexOf("输入参数：");
        if (paramIdx >= 0) {
            int end = message.indexOf("\n", paramIdx);
            String params = end > paramIdx ? message.substring(paramIdx, end) : message.substring(paramIdx);
            return params;
        }
        // 退化：返回完整 message（包含任务目标 + 前置输出）
        return message;
    }

    /**
     * 从 message 中提取前置任务结果
     */
    private String extractPreviousResult(String message) {
        if (message == null) return "";
        int idx = message.indexOf("【前置任务输出】");
        if (idx >= 0) {
            return message.substring(idx);
        }
        return "";
    }
}