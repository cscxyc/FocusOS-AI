package com.focusos.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.focusos.dto.response.InterviewEvaluationResponse;
import com.focusos.dto.response.InterviewQuestionResponse;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sprint 7-B: Interview Agent — 模拟面试专家
 * Sprint 7-C-A: 升级为 DTO + ObjectMapper + JSON Schema 校验，解决 LLM 输出特殊字符导致解析失败问题
 * <p>
 * 职责：
 * 1. 面试题生成（基于用户真实经历 + 目标 JD + CareerAnalysisReport + 简历优化结果）
 * 2. 模拟面试对话评价（用户回答 → AI 评分 + strengths/weaknesses/improvement）
 * <p>
 * 质量控制（Sprint 7-B）：
 * - 所有面试题必须结合用户真实经历，禁止生成通用面试题
 * - 项目深挖问题必须来源 Personal RAG（如 FocusOS AI 项目：为什么选 Milvus / 如何做用户隔离 / 为什么用 SSE）
 * - 评价必须具体（指出用户回答中的亮点、漏洞、改进方向）
 * - 资料不足时明确提示，score ≤ 40
 * <p>
 * 输出 JSON 格式（功能1 面试题生成）：
 * {
 *   "interviewQuestions": [
 *     {
 *       "type": "技术问题",
 *       "question": "",
 *       "difficulty": "简单/中等/困难",
 *       "expectedAnswer": "",
 *       "userProjectReference": "",
 *       "followUpQuestions": []
 *     }
 *   ]
 * }
 * <p>
 * 输出 JSON 格式（功能2 模拟面试评价）：
 * {
 *   "score": 85,
 *   "strengths": [],
 *   "weaknesses": [],
 *   "improvement": []
 * }
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewAgent implements FocusAgent {

    private final ChatLanguageModel chatLanguageModel;
    private final AgentPromptProvider promptProvider;
    private final LLMJsonSanitizer jsonSanitizer;
    private final ObjectMapper objectMapper;

    @Override
    public String type() {
        return "interview";
    }

    // ============================================================
    // 功能 1：面试题生成
    // ============================================================

    /**
     * 基于用户真实经历 + 目标 JD + CareerAnalysisReport + 简历优化结果生成面试题
     * <p>
     * 必须覆盖类别：Java 基础 / Spring Boot / AI 应用开发 / RAG / Agent / 项目深挖
     *
     * @param jobDescription        目标 JD
     * @param workflowContextText   Personal RAG 渲染的用户画像（含来源）
     * @param careerAnalysisResult  CareerAnalysisReport 中 CAREER_ANALYSIS 任务的输出
     * @param resumeOptimizationResult RESUME_OPTIMIZATION 任务的输出
     * @return 面试题 JSON
     */
    /**
     * 兼容旧调用（无 Memory 上下文）
     */
    public String generateInterviewQuestions(String jobDescription,
                                              String workflowContextText,
                                              String careerAnalysisResult,
                                              String resumeOptimizationResult) {
        return generateInterviewQuestions(jobDescription, workflowContextText, careerAnalysisResult, resumeOptimizationResult, null);
    }

    /**
     * Sprint 8-C：主方法，带 UserMemoryContext（长期成长记忆 + 项目深挖优先级 Memory > Resume > RAG）
     */
    public String generateInterviewQuestions(String jobDescription,
                                              String workflowContextText,
                                              String careerAnalysisResult,
                                              String resumeOptimizationResult,
                                              UserMemoryContext memoryContext) {
        boolean profileEmpty = isBlank(workflowContextText)
                || workflowContextText.contains("RAG 检索失败")
                || workflowContextText.contains("降级为通用上下文");

        // Sprint 8-C：长期记忆块（通用记忆展示 + 项目深挖优先级约束）
        String memorySection;
        if (memoryContext != null) {
            memorySection = memoryContext.renderAsPromptSection()
                    + "\n"
                    + memoryContext.renderProjectPrioritiesForInterview();
        } else {
            memorySection = "【长期成长记忆】（未加载，按默认优先级 Resume + RAG 出题）\n";
        }

        String prompt = String.format("""
                你是 FocusOS AI 平台的资深技术面试官（10年面试经验）。请基于以下信息生成定制化面试题。

                【目标职位 JD】
                %s

                【用户画像（来自 Personal RAG，含来源标注）】
                %s

                【岗位匹配分析结果（CareerAnalysisReport）】
                %s

                【简历优化结果（STAR 改写）】
                %s

                %s

                %s

                严格按以下 JSON 格式输出（不要输出任何其他内容，不要使用 Markdown 代码块）：
                {
                  "interviewQuestions": [
                    {
                      "type": "技术问题",
                      "category": "Java基础",
                      "question": "面试题目（必须具体，结合用户经历）",
                      "difficulty": "简单|中等|困难",
                      "expectedAnswer": "参考答案要点（3-5 句）",
                      "userProjectReference": "用户可引用的真实项目/实习经历（必须来自 Personal RAG，含来源标注）",
                      "followUpQuestions": ["追问1", "追问2"]
                    }
                  ]
                }

                【硬性要求 — 必须严格遵守】
                1. 必须覆盖以下 6 个类别，每个类别至少 1 题，总题数 8-12 题：
                   - Java基础（如 HashMap 原理、JVM 内存模型、并发编程）
                   - Spring Boot（如 IoC/AOP、自动装配、Spring Boot 3 特性）
                   - AI应用开发（如 LLM API 调用、Prompt 工程、Function Calling）
                   - RAG（如文档分块策略、Embedding 模型选型、向量检索 minScore 调优）
                   - Agent（如 Multi-Agent 架构、Agent 通信、Workflow 编排）
                   - 项目深挖（必须基于用户真实项目，如 FocusOS AI 项目的 Milvus 选型 / 用户隔离 / SSE 推送）
                2. 项目深挖类问题必须来源 Personal RAG，禁止编造用户没做过的项目
                3. 每个问题必须提供 userProjectReference，引用用户真实经历（含来源文档名）
                4. 项目深挖问题必须有针对性追问（followUpQuestions），如：
                   - "为什么选择 Milvus 而不是 Pinecone/Weaviate？"
                   - "如何实现用户隔离（userId metadata 过滤）？"
                   - "为什么使用 SSE 而不是 WebSocket？"
                5. difficulty 分布：至少 2 题困难、3 题中等、3 题简单
                6. 如果用户资料不足：在 category=项目深挖 的 userProjectReference 中明确提示"资料不足，建议补充项目文档"
                """,
                isBlank(jobDescription) ? "（未提供 JD）" : jobDescription,
                profileEmpty ? "（用户资料不足，RAG 检索为空或失败）" : workflowContextText,
                isBlank(careerAnalysisResult) ? "（无 Career 分析结果）" : truncate(careerAnalysisResult, 3000),
                isBlank(resumeOptimizationResult) ? "（无简历优化结果）" : truncate(resumeOptimizationResult, 3000),
                memorySection,
                profileEmpty ? "【警告】用户资料不足，项目深挖问题应明确提示用户补充项目文档。" : "");

        String fullPrompt = promptProvider.interviewSystemPrompt() + "\n\n" + prompt;

        try {
            String response = chatLanguageModel.chat(fullPrompt);
            // Sprint 7-C-A: 通过 LLMJsonSanitizer 清洗 + 解析为 DTO + 序列化保证 JSON 稳定性
            InterviewQuestionResponse dto = parseQuestionsResponse(response);
            return jsonSanitizer.serialize(dto);
        } catch (Exception e) {
            log.error("Failed to generate interview questions", e);
            InterviewQuestionResponse fallback = InterviewQuestionResponse.builder()
                    .interviewQuestions(new ArrayList<>())
                    .build();
            return jsonSanitizer.serialize(fallback);
        }
    }

    /**
     * Sprint 7-C-A: 解析 LLM 面试题输出为 DTO（含 JSON Schema 校验）
     * <p>
     * 流程：
     * 1. LLMJsonSanitizer 清洗原始输出（修复正则反斜杠、控制字符、截断等）
     * 2. ObjectMapper 解析为 InterviewQuestionResponse DTO
     * 3. JSON Schema 校验：每个问题必须有 question 字段，category 必须覆盖 6 大类别
     * 4. 若解析失败：降级为手动 Map 解析 + 字段补全
     */
    private InterviewQuestionResponse parseQuestionsResponse(String llmOutput) {
        // 1. 清洗 + 解析为 DTO
        InterviewQuestionResponse dto = jsonSanitizer.sanitizeToObject(llmOutput, InterviewQuestionResponse.class);
        if (dto != null && dto.getInterviewQuestions() != null && !dto.getInterviewQuestions().isEmpty()) {
            // 2. Schema 校验：过滤掉没有 question 字段的无效条目
            List<InterviewQuestionResponse.Question> valid = new ArrayList<>();
            for (InterviewQuestionResponse.Question q : dto.getInterviewQuestions()) {
                if (q.getQuestion() != null && !q.getQuestion().isBlank()) {
                    // 补全缺失字段
                    if (q.getType() == null || q.getType().isBlank()) q.setType("技术问题");
                    if (q.getCategory() == null || q.getCategory().isBlank()) q.setCategory("未分类");
                    if (q.getDifficulty() == null || q.getDifficulty().isBlank()) q.setDifficulty("中等");
                    if (q.getFollowUpQuestions() == null) q.setFollowUpQuestions(new ArrayList<>());
                    valid.add(q);
                }
            }
            dto.setInterviewQuestions(valid);
            return dto;
        }

        // 3. 降级：手动 Map 解析
        log.warn("DTO parsing failed or empty, fallback to Map parsing");
        Map<String, Object> map = jsonSanitizer.sanitizeToMap(llmOutput);
        if (map.isEmpty()) {
            return InterviewQuestionResponse.builder().interviewQuestions(new ArrayList<>()).build();
        }
        Object questionsObj = map.get("interviewQuestions");
        if (!(questionsObj instanceof List<?> rawList)) {
            return InterviewQuestionResponse.builder().interviewQuestions(new ArrayList<>()).build();
        }
        List<InterviewQuestionResponse.Question> questions = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof Map<?, ?> m) {
                String question = getAsString(m, "question");
                if (question == null || question.isBlank()) continue;
                questions.add(InterviewQuestionResponse.Question.builder()
                        .type(getAsString(m, "type", "技术问题"))
                        .category(getAsString(m, "category", "未分类"))
                        .question(question)
                        .difficulty(getAsString(m, "difficulty", "中等"))
                        .expectedAnswer(getAsString(m, "expectedAnswer", ""))
                        .userProjectReference(getAsString(m, "userProjectReference", ""))
                        .followUpQuestions(toStringList(m.get("followUpQuestions")))
                        .build());
            }
        }
        return InterviewQuestionResponse.builder().interviewQuestions(questions).build();
    }

    private List<String> toStringList(Object obj) {
        if (obj instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object o : list) {
                if (o != null) result.add(o.toString());
            }
            return result;
        }
        return new ArrayList<>();
    }

    private String getAsString(Map<?, ?> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private String getAsString(Map<?, ?> map, String key, String defaultValue) {
        Object v = map.get(key);
        if (v == null || v.toString().isBlank()) return defaultValue;
        return v.toString();
    }

    // ============================================================
    // 功能 2：模拟面试对话评价
    // ============================================================

    /**
     * 对用户回答进行评价
     *
     * @param question             面试官问题
     * @param expectedAnswer       参考答案
     * @param userAnswer           用户回答
     * @param userProjectReference 用户可引用的项目经历
     * @param workflowContextText  Personal RAG 上下文（验证用户是否说真话）
     * @return 评价 JSON：{score, strengths, weaknesses, improvement}
     */
    public String evaluateAnswer(String question,
                                  String expectedAnswer,
                                  String userAnswer,
                                  String userProjectReference,
                                  String workflowContextText) {
        boolean profileEmpty = isBlank(workflowContextText)
                || workflowContextText.contains("RAG 检索失败")
                || workflowContextText.contains("降级为通用上下文");

        String prompt = String.format("""
                你是 FocusOS AI 平台的资深技术面试官。请评价用户对面试问题的回答。

                【面试官问题】
                %s

                【参考答案要点】
                %s

                【用户可引用的项目经历（来自 Personal RAG）】
                %s

                【用户回答】
                %s

                【用户真实背景资料（用于验证回答真实性）】
                %s

                %s

                严格按以下 JSON 格式输出（不要输出其他内容，不要 Markdown 代码块）：
                {
                  "score": <0-100 整数，回答质量评分>,
                  "strengths": [
                    "亮点1（具体指出用户回答中的优点，引用回答原文）",
                    "亮点2"
                  ],
                  "weaknesses": [
                    "弱点1（具体指出回答中的不足或错误）",
                    "弱点2"
                  ],
                  "improvement": [
                    "改进建议1（具体可操作）",
                    "改进建议2"
                  ],
                  "factCheck": {
                    "fabricated": false,
                    "fabricationDetails": "如检测到编造经历，列出具体内容；否则为空字符串"
                  }
                }

                【评价标准 — 必须严格遵守】
                1. score 分级：85-100 优秀 / 70-84 良好 / 55-69 合格 / 40-54 较弱 / 0-39 不合格
                2. strengths 必须具体引用用户回答原文，禁止泛泛而谈（如"回答流畅"不算具体亮点）
                3. weaknesses 必须指出具体技术错误、遗漏的关键点、表达不清的地方
                4. improvement 必须可操作（如"补充说明 Milvus 的 minScore 调优从 0.5 到 0.25 的原因"）
                5. factCheck.fabricated：检测用户是否编造未做过的经历，对照 Personal RAG 真实资料
                   - 若用户提到 RAGAgent/CareerAgent/FocusOS AI 等项目，必须验证 Personal RAG 中是否存在
                   - 若编造：fabricated=true，score 不超过 30，并在 fabricationDetails 中列出编造内容
                6. 资料不足时（Personal RAG 为空）：factCheck 可降级为 fabricated=false，但 score 不超过 50
                """,
                isBlank(question) ? "（无问题）" : question,
                isBlank(expectedAnswer) ? "（无参考答案）" : expectedAnswer,
                isBlank(userProjectReference) ? "（无项目经历引用）" : userProjectReference,
                isBlank(userAnswer) ? "（用户未作答）" : userAnswer,
                profileEmpty ? "（用户资料不足，无法验证回答真实性）" : workflowContextText,
                profileEmpty ? "【警告】用户资料不足，无法进行事实核查，score 不应超过 50。" : "");

        String fullPrompt = promptProvider.mockInterviewSystemPrompt() + "\n\n" + prompt;

        try {
            String response = chatLanguageModel.chat(fullPrompt);
            // Sprint 7-C-A: 通过 LLMJsonSanitizer 清洗 + 解析为 DTO + 序列化保证 JSON 稳定性
            InterviewEvaluationResponse dto = parseEvaluationResponse(response);
            return jsonSanitizer.serialize(dto);
        } catch (Exception e) {
            log.error("Failed to evaluate interview answer", e);
            InterviewEvaluationResponse fallback = InterviewEvaluationResponse.builder()
                    .score(0)
                    .strengths(new ArrayList<>())
                    .weaknesses(List.of("评价失败"))
                    .improvement(List.of("请稍后重试"))
                    .factCheck(InterviewEvaluationResponse.FactCheck.builder()
                            .fabricated(false)
                            .fabricationDetails("")
                            .build())
                    .build();
            return jsonSanitizer.serialize(fallback);
        }
    }

    /**
     * Sprint 7-C-A: 解析 LLM 评价输出为 DTO（含 JSON Schema 校验）
     */
    private InterviewEvaluationResponse parseEvaluationResponse(String llmOutput) {
        InterviewEvaluationResponse dto = jsonSanitizer.sanitizeToObject(llmOutput, InterviewEvaluationResponse.class);
        if (dto != null && dto.getScore() != null) {
            // Schema 校验：补全缺失字段
            if (dto.getStrengths() == null) dto.setStrengths(new ArrayList<>());
            if (dto.getWeaknesses() == null) dto.setWeaknesses(new ArrayList<>());
            if (dto.getImprovement() == null) dto.setImprovement(new ArrayList<>());
            if (dto.getFactCheck() == null) {
                dto.setFactCheck(InterviewEvaluationResponse.FactCheck.builder()
                        .fabricated(false)
                        .fabricationDetails("")
                        .build());
            }
            return dto;
        }

        // 降级：手动 Map 解析
        log.warn("Evaluation DTO parsing failed, fallback to Map parsing");
        Map<String, Object> map = jsonSanitizer.sanitizeToMap(llmOutput);
        if (map.isEmpty()) {
            return InterviewEvaluationResponse.builder()
                    .score(0)
                    .strengths(new ArrayList<>())
                    .weaknesses(List.of("评价解析失败"))
                    .improvement(new ArrayList<>())
                    .factCheck(InterviewEvaluationResponse.FactCheck.builder()
                            .fabricated(false).fabricationDetails("").build())
                    .build();
        }
        InterviewEvaluationResponse.InterviewEvaluationResponseBuilder builder = InterviewEvaluationResponse.builder()
                .score(getAsInt(map, "score", 0))
                .strengths(toStringList(map.get("strengths")))
                .weaknesses(toStringList(map.get("weaknesses")))
                .improvement(toStringList(map.get("improvement")));
        Object fc = map.get("factCheck");
        if (fc instanceof Map<?, ?> fcMap) {
            builder.factCheck(InterviewEvaluationResponse.FactCheck.builder()
                    .fabricated(getAsBool(fcMap, "fabricated", false))
                    .fabricationDetails(getAsString(fcMap, "fabricationDetails", ""))
                    .build());
        } else {
            builder.factCheck(InterviewEvaluationResponse.FactCheck.builder()
                    .fabricated(false).fabricationDetails("").build());
        }
        return builder.build();
    }

    private Integer getAsInt(Map<?, ?> map, String key, int defaultValue) {
        Object v = map.get(key);
        if (v == null) return defaultValue;
        if (v instanceof Number) return ((Number) v).intValue();
        try {
            return Integer.parseInt(v.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private Boolean getAsBool(Map<?, ?> map, String key, boolean defaultValue) {
        Object v = map.get(key);
        if (v == null) return defaultValue;
        if (v instanceof Boolean) return (Boolean) v;
        return defaultValue;
    }

    /**
     * 生成最终面试总结（多轮问答后）
     *
     * @param conversationJson 对话历史 JSON（[{question, answer, evaluation}, ...]）
     * @param jobDescription   目标 JD
     * @return 最终评价 JSON
     */
    public String generateFinalEvaluation(String conversationJson, String jobDescription) {
        String prompt = String.format("""
                你是 FocusOS AI 平台的资深技术面试官。请基于完整的模拟面试对话历史，生成最终面试评价。

                【目标职位 JD】
                %s

                【模拟面试对话历史】
                %s

                严格按以下 JSON 格式输出（不要输出其他内容，不要 Markdown 代码块）：
                {
                  "score": <0-100 整数，综合面试评分>,
                  "strengths": ["综合优势1", "优势2"],
                  "weaknesses": ["综合弱点1", "弱点2"],
                  "improvement": ["改进建议1", "建议2", "建议3"],
                  "jobReadiness": "求职准备度评估（高度准备/良好准备/需要加强/准备不足）",
                  "focusAreas": ["下一步重点练习方向1", "方向2"]
                }

                要求：
                1. score 基于全部问答的综合表现，不是简单平均
                2. strengths/weaknesses 必须基于对话历史中的具体回答
                3. improvement 至少 3 条具体可操作建议
                4. jobReadiness 给出明确的求职准备度判断
                """,
                isBlank(jobDescription) ? "（未提供 JD）" : jobDescription,
                isBlank(conversationJson) ? "（无对话历史）" : truncate(conversationJson, 5000));

        String fullPrompt = promptProvider.mockInterviewSystemPrompt() + "\n\n" + prompt;

        try {
            String response = chatLanguageModel.chat(fullPrompt);
            // Sprint 7-C-A: 通过 LLMJsonSanitizer 清洗 + 解析为 DTO + 序列化保证 JSON 稳定性
            InterviewEvaluationResponse dto = parseFinalEvaluationResponse(response);
            return jsonSanitizer.serialize(dto);
        } catch (Exception e) {
            log.error("Failed to generate final evaluation", e);
            InterviewEvaluationResponse fallback = InterviewEvaluationResponse.builder()
                    .score(0)
                    .strengths(new ArrayList<>())
                    .weaknesses(List.of("评估失败"))
                    .improvement(List.of("请稍后重试"))
                    .jobReadiness("评估失败")
                    .focusAreas(new ArrayList<>())
                    .build();
            return jsonSanitizer.serialize(fallback);
        }
    }

    /**
     * Sprint 7-C-A: 解析 LLM 最终评价输出为 DTO
     */
    private InterviewEvaluationResponse parseFinalEvaluationResponse(String llmOutput) {
        InterviewEvaluationResponse dto = jsonSanitizer.sanitizeToObject(llmOutput, InterviewEvaluationResponse.class);
        if (dto != null && dto.getScore() != null) {
            if (dto.getStrengths() == null) dto.setStrengths(new ArrayList<>());
            if (dto.getWeaknesses() == null) dto.setWeaknesses(new ArrayList<>());
            if (dto.getImprovement() == null) dto.setImprovement(new ArrayList<>());
            if (dto.getFocusAreas() == null) dto.setFocusAreas(new ArrayList<>());
            if (dto.getJobReadiness() == null) dto.setJobReadiness("未评估");
            return dto;
        }

        // 降级：手动 Map 解析
        Map<String, Object> map = jsonSanitizer.sanitizeToMap(llmOutput);
        if (map.isEmpty()) {
            return InterviewEvaluationResponse.builder()
                    .score(0)
                    .strengths(new ArrayList<>())
                    .weaknesses(List.of("最终评价解析失败"))
                    .improvement(new ArrayList<>())
                    .jobReadiness("解析失败")
                    .focusAreas(new ArrayList<>())
                    .build();
        }
        return InterviewEvaluationResponse.builder()
                .score(getAsInt(map, "score", 0))
                .strengths(toStringList(map.get("strengths")))
                .weaknesses(toStringList(map.get("weaknesses")))
                .improvement(toStringList(map.get("improvement")))
                .jobReadiness(getAsString(map, "jobReadiness", "未评估"))
                .focusAreas(toStringList(map.get("focusAreas")))
                .build();
    }

    // ============================================================
    // Workflow 路由入口
    // ============================================================

    @Override
    public String handle(String message, Long userId, String context) {
        // 从 context 解析任务类型
        String taskType = extractTaskType(context);
        String workflowContextText = extractWorkflowContext(context);
        String jd = extractJDFromMessage(message);
        String previousResult = extractPreviousResult(message);

        if ("INTERVIEW_PREPARATION".equals(taskType)) {
            // Task5: 面试题生成
            // 前置任务输出是 CAREER_ANALYSIS 的结果
            log.info("InterviewAgent.handle → INTERVIEW_PREPARATION, userId={}", userId);
            return generateInterviewQuestions(jd, workflowContextText, previousResult, null);
        }
        if ("MOCK_INTERVIEW".equals(taskType)) {
            // Task6: 模拟面试（生成初始面试题供后续对话使用）
            // 前置任务输出是 INTERVIEW_PREPARATION 的结果
            log.info("InterviewAgent.handle → MOCK_INTERVIEW, userId={}", userId);
            // 如果前置任务已生成面试题，直接复用；否则重新生成
            if (!isBlank(previousResult) && previousResult.contains("interviewQuestions")) {
                return previousResult;
            }
            return generateInterviewQuestions(jd, workflowContextText, previousResult, null);
        }

        // 通用对话：直接生成面试题
        log.info("InterviewAgent.handle → default (generate questions), userId={}", userId);
        return generateInterviewQuestions(jd, workflowContextText, null, null);
    }

    // ============================================================
    // 工具方法
    // ============================================================

    /**
     * 解析面试题 JSON 为 List<Map>（供 Service 层使用）
     * <p>
     * Sprint 7-C-A: 通过 LLMJsonSanitizer 清洗后再解析，兼容历史存储的格式异常 JSON
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> parseInterviewQuestions(String questionsJson) {
        if (isBlank(questionsJson)) return List.of();
        try {
            // Sprint 7-C-A: 先清洗再解析，兼容历史 malformed JSON
            String sanitized = jsonSanitizer.sanitize(questionsJson);
            Map<String, Object> root = objectMapper.readValue(sanitized, new TypeReference<Map<String, Object>>() {});
            Object questions = root.get("interviewQuestions");
            if (questions instanceof List) {
                return (List<Map<String, Object>>) questions;
            }
        } catch (Exception e) {
            log.warn("Failed to parse interview questions JSON: {}", e.getMessage());
        }
        return List.of();
    }

    /**
     * 解析对话历史 JSON
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> parseConversation(String conversationJson) {
        if (isBlank(conversationJson)) return new ArrayList<>();
        try {
            return objectMapper.readValue(conversationJson, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse conversation JSON", e);
        }
        return new ArrayList<>();
    }

    /**
     * 序列化对话历史为 JSON
     */
    public String serializeConversation(List<Map<String, Object>> conversation) {
        try {
            return objectMapper.writeValueAsString(conversation);
        } catch (Exception e) {
            log.error("Failed to serialize conversation", e);
            return "[]";
        }
    }

    /**
     * 创建对话条目
     */
    public Map<String, Object> createConversationEntry(String question, String expectedAnswer,
                                                        String userProjectReference, String userAnswer,
                                                        String evaluationJson) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("question", question);
        entry.put("expectedAnswer", expectedAnswer);
        entry.put("userProjectReference", userProjectReference);
        entry.put("userAnswer", userAnswer);
        entry.put("evaluation", evaluationJson);
        return entry;
    }

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

    private String extractWorkflowContext(String context) {
        if (context == null) return "";
        int idx = context.indexOf("【用户原始目标】");
        if (idx >= 0) {
            return context.substring(idx);
        }
        return context;
    }

    private String extractJDFromMessage(String message) {
        if (message == null) return "";
        int paramIdx = message.indexOf("输入参数:");
        if (paramIdx < 0) paramIdx = message.indexOf("输入参数：");
        if (paramIdx >= 0) {
            int end = message.indexOf("\n", paramIdx);
            return end > paramIdx ? message.substring(paramIdx, end) : message.substring(paramIdx);
        }
        return message;
    }

    private String extractPreviousResult(String message) {
        if (message == null) return "";
        int idx = message.indexOf("【前置任务输出】");
        if (idx >= 0) {
            return message.substring(idx);
        }
        return "";
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
