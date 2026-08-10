package com.focusos.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.focusos.dto.response.ResumeEvaluationResponse;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Sprint 8-A: Resume Evaluator Agent — 简历质量评估专家
 * <p>
 * 职责：
 * 根据目标 JD + ResumeVersion 内容 + Personal RAG 用户画像，
 * 对简历进行多维度 AI 质量评分，输出结构化评估结果。
 * <p>
 * 评分体系（总分 100）：
 * - matchScore          JD 匹配度（30 分）：技术关键词匹配 + 岗位要求覆盖 + 项目相关性
 * - atsScore            ATS 关键词评分（20 分）：检测核心技术关键词覆盖
 * - starScore           STAR 经历评分（25 分）：Situation/Task/Action/Result 四要素 + 项目深度
 * - completenessScore   完整度评分（10 分）：教育背景/技能/项目/实习四 section
 * - 项目深度（15 分）：含在 starScore 子项中
 * <p>
 * 质量控制：
 * - 禁止编造候选人经历：所有优势必须来自简历原文或 Personal RAG 真实资料
 * - 所有不足必须对应 JD 要求：禁止泛泛而谈
 * - 评分必须客观：模拟真实 HR 筛选的严格程度
 * <p>
 * 输出 JSON 格式：
 * {
 *   "score": 87,
 *   "matchScore": 80,
 *   "atsScore": 90,
 *   "starScore": 85,
 *   "completenessScore": 95,
 *   "strengths": [],
 *   "weaknesses": [],
 *   "missingKeywords": [],
 *   "keywordMatches": [{"keyword":"","status":"MATCH|MISSING","evidence":""}],
 *   "sectionScores": {"summary":0,"experience":0,"project":0,"skills":0},
 *   "suggestions": [],
 *   "recommendedActions": []
 * }
 * <p>
 * 技术实现（复用 Sprint 7-C-A 稳定化方案）：
 * - DTO + ObjectMapper + LLMJsonSanitizer 保证 JSON 100% 合法
 * - LLMCallContext (ThreadLocal) + LLMLoggingService 记录调用日志，agentType="resume_evaluator"
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeEvaluatorAgent implements FocusAgent {

    private final ChatLanguageModel chatLanguageModel;
    private final AgentPromptProvider promptProvider;
    private final LLMJsonSanitizer jsonSanitizer;
    private final ObjectMapper objectMapper;

    @Override
    public String type() {
        return "resume_evaluator";
    }

    // ============================================================
    // 核心方法：简历评估
    // ============================================================

    /**
     * 根据 JD + 简历内容 + 用户画像进行 AI 质量评分
     *
     * @param jobDescription   目标 JD
     * @param resumeContent    ResumeVersion 的 Markdown 内容
     * @param profileContext   Personal RAG 用户画像（用于真实性核查，可为 null）
     * @return 结构化评估结果 JSON（保证 100% 合法）
     */
    /**
     * 兼容旧调用（无 Memory 上下文）
     */
    public String evaluateResume(String jobDescription,
                                  String resumeContent,
                                  UserProfileContext profileContext) {
        return evaluateResume(jobDescription, resumeContent, profileContext, null);
    }

    /**
     * Sprint 8-C：主方法，带 UserMemoryContext（长期成长记忆 + growthAlignmentScore 成长一致性评分）
     */
    public String evaluateResume(String jobDescription,
                                  String resumeContent,
                                  UserProfileContext profileContext,
                                  UserMemoryContext memoryContext) {
        boolean profileEmpty = profileContext == null
                || !profileContext.isRetrievalSuccess()
                || (profileContext.getProfileText() == null || profileContext.getProfileText().isBlank());
        String profileText = profileEmpty
                ? "（Personal RAG 检索为空或失败，无法进行真实性核查）"
                : profileContext.renderWithSources();

        // Sprint 8-C：长期记忆 + growthAlignmentScore 评估规则
        String memorySection;
        if (memoryContext != null) {
            StringBuilder sb = new StringBuilder();
            sb.append(memoryContext.renderAsPromptSection()).append("\n");
            sb.append("【特别新增：growthAlignmentScore 成长一致性评分（0-100，严格按以下规则评估）】\n");
            sb.append("目的：验证简历中写的技能/项目，是否与用户长期成长状态（Personal Memory）一致。\n");
            sb.append("评分规则：\n");
            sb.append("- 先扫描简历中的技能/项目，再匹配用户 Memory 中的同 keyword 条目：\n");
            sb.append("  ① 简历提到技能 X → Memory 中也有 X（confidence≥0.7）且 value 描述更深入（如简历写 RAG / Memory 写 Milvus 优化）→ 每条 +5~10 分（最高 +30 分）\n");
            sb.append("  ② 简历写项目 Y → Memory 中也有 PROJECT 类型完成记录（已完成里程碑）→ 每个 +10 分（最高 +20）\n");
            sb.append("  ③ 简历写了「精通XX」高级措辞 → Memory 中无任何对应 skill 或 confidence<0.5 → 每条扣 8 分（最多 -30）\n");
            sb.append("  ④ 无任何 Memory 数据 → 基础分 60 分（中性）\n");
            sb.append("- 最终 growthAlignmentScore = 60(基础分) + 匹配加分 - 虚高扣分，限制在 0-100 内；\n");
            sb.append("- 在 strengths/weaknesses 中需要各至少 1 条专门指出成长一致性的亮点/不足；\n");
            sb.append("- 在 suggestions 中给出「强化成长一致性」的建议（如「用户已通过 Memory 沉淀 Milvus 优化经验，建议在简历 RAG 项目中明确写出调优数据」）。\n");
            memorySection = sb.toString();
        } else {
            memorySection = "【长期成长记忆】（未加载，growthAlignmentScore 默认 60）\n";
        }

        String prompt = String.format("""
                请根据以下信息对候选人简历进行严格的技术评估。

                【目标职位 JD】
                %s

                【候选人简历内容（Markdown）】
                %s

                【候选人 Personal RAG 真实资料（用于真实性核查）】
                %s

                %s

                %s

                严格按以下 JSON 格式输出（不要输出任何其他内容，不要使用 Markdown 代码块）：
                {
                  "score": <0-100 整数，综合总分>,
                  "matchScore": <0-100 整数，JD 匹配度>,
                  "atsScore": <0-100 整数，ATS 关键词评分>,
                  "starScore": <0-100 整数，STAR 经历评分（含项目深度子项）>,
                  "completenessScore": <0-100 整数，完整度评分>,
                  "growthAlignmentScore": <0-100 整数，Sprint8-C 新增：简历技能/项目描述与 Personal Memory 长期成长状态的一致性评分>,
                  "strengths": [
                    "优势1（必须来自简历原文或 Personal RAG 真实资料，引用具体内容）",
                    "优势2"
                  ],
                  "weaknesses": [
                    "不足1（必须对应 JD 要求，指出简历缺失的内容）",
                    "不足2"
                  ],
                  "missingKeywords": [
                    "JD 要求但简历缺失的关键词1",
                    "关键词2"
                  ],
                  "keywordMatches": [
                    {
                      "keyword": "RAG",
                      "status": "MATCH",
                      "evidence": "简历中出现的原文片段（MISSING 时为空字符串）"
                    }
                  ],
                  "sectionScores": {
                    "summary": <0-100 整数>,
                    "experience": <0-100 整数>,
                    "project": <0-100 整数>,
                    "skills": <0-100 整数>
                  },
                  "suggestions": [
                    "可操作的优化建议1",
                    "建议2"
                  ],
                  "recommendedActions": [
                    "具体的下一步行动1",
                    "行动2"
                  ]
                }

                【评估要求 — 必须严格遵守】
                1. score 综合总分 = matchScore*0.3 + atsScore*0.2 + starScore*0.25 + completenessScore*0.1 + 项目深度*0.15（项目深度作为 starScore 子项已计入，无需单独字段）。
                2. matchScore：评估简历对 JD 技术关键词的匹配率 + 岗位要求覆盖率 + 项目相关性。
                   - 示例：JD 要求 RAG/Milvus/LangChain4j，简历包含 RAG/Milvus → matchScore=66（2/3 关键词匹配）。
                3. atsScore：检测简历中是否包含 JD 要求的核心技术关键词（如 Java/Spring Boot/Redis/MySQL/RAG/Agent/Milvus/LangChain4j/Docker/Kubernetes）。
                4. starScore：检查经历描述是否包含 Situation/Task/Action/Result 四要素。
                   - 低质量描述："负责开发接口" → 扣分（缺少 STAR 四要素）。
                   - 高质量描述："针对企业财务系统收款流程，设计合同回写接口，优化审批链路，提升数据同步稳定性" → 加分。
                   - 项目深度：是否包含技术架构、核心难点、解决方案、工程指标。
                5. completenessScore：检查是否包含教育背景、技术栈、项目经历、实习/工作经历四个 section。
                6. keywordMatches：必须列出 JD 中每个核心技术关键词的匹配状态（MATCH/MISSING）和证据（简历原文片段）。
                7. missingKeywords：必须列出 JD 要求但简历缺失的关键词。
                8. sectionScores：必须给出 summary/experience/project/skills 四个 section 的评分。
                9. strengths/weaknesses/suggestions/recommendedActions 每项至少 2 条。
                10. 真实性核查：对照 Personal RAG 真实资料，所有优势必须来自简历原文或 Personal RAG，禁止编造候选人未做过的经历。
                11. 所有不足必须对应 JD 要求（如"JD 要求 Kubernetes 但简历未提及"），禁止泛泛而谈（如"表达能力不足"不算具体不足）。
                12. 评分必须客观：不要给鼓励性评价，模拟真实 HR 筛选的严格程度。
                13. 资料不足时（Personal RAG 为空）：仍可基于简历内容评估，但 strengths 中不能引用 Personal RAG 未提供的内容，且 score 不超过 60。
                14. 【JSON 格式严格要求】在 JSON 字符串值内部，禁止使用未转义的双引号（"）引用文本。如需引用 JD 或简历原文，请使用单引号（'）或书名号（《》）代替。例如：正确写法 "JD 要求'高并发'经验"，错误写法 "JD 要求"高并发"经验"。
                """,
                isBlank(jobDescription) ? "（未提供 JD）" : truncate(jobDescription, 4000),
                isBlank(resumeContent) ? "（简历内容为空）" : truncate(resumeContent, 6000),
                profileText,
                memorySection,
                profileEmpty ? "【警告】Personal RAG 资料不足，无法进行真实性核查，score 不应超过 60，strengths 不能引用未验证的经历。" : "");

        String fullPrompt = promptProvider.resumeEvaluatorSystemPrompt() + "\n\n" + prompt;

        try {
            String response = chatLanguageModel.chat(fullPrompt);
            // Sprint 8-A: 通过 LLMJsonSanitizer 清洗 + 解析为 DTO + 序列化保证 JSON 稳定性
            ResumeEvaluationResponse dto = parseEvaluationResponse(response);

            // Sprint 8-A: 重试机制 — 若首次解析失败，追加 JSON 格式提醒后重试一次
            if (dto == null || dto.getScore() == null) {
                log.warn("First evaluation parse failed, retrying with JSON format reminder...");
                String retryPrompt = fullPrompt + "\n\n【重要提醒】你上次的输出包含 JSON 格式错误（字符串值内部有未转义的双引号）。请重新输出严格合法的 JSON。在字符串值内部，禁止使用双引号，请用单引号代替。只输出 JSON，不要输出任何其他内容。";
                String retryResponse = chatLanguageModel.chat(retryPrompt);
                dto = parseEvaluationResponse(retryResponse);
            }

            return jsonSanitizer.serialize(dto);
        } catch (Exception e) {
            log.error("Failed to evaluate resume", e);
            ResumeEvaluationResponse fallback = buildFallbackResponse();
            return jsonSanitizer.serialize(fallback);
        }
    }

    // ============================================================
    // DTO 解析（含 Schema 校验 + 降级 Map 解析）
    // ============================================================

    /**
     * 解析 LLM 评估输出为 DTO（复用 Sprint 7-C-A 方案）
     * <p>
     * 流程：
     * 1. LLMJsonSanitizer 清洗原始输出
     * 2. ObjectMapper 解析为 ResumeEvaluationResponse DTO
     * 3. Schema 校验：补全缺失字段，钳制评分范围到 0-100
     * 4. 若解析失败：降级为手动 Map 解析 + 字段补全
     */
    private ResumeEvaluationResponse parseEvaluationResponse(String llmOutput) {
        // 1. 清洗 + 解析为 DTO
        ResumeEvaluationResponse dto = jsonSanitizer.sanitizeToObject(llmOutput, ResumeEvaluationResponse.class);
        if (dto == null || dto.getScore() == null) {
            log.warn("ResumeEvaluation DTO parsing failed. Full LLM output (len={}):\n{}", llmOutput.length(), llmOutput);
        }
        if (dto != null && dto.getScore() != null) {
            normalizeDto(dto);
            return dto;
        }

        // 2. 降级：手动 Map 解析
        log.warn("Evaluation DTO parsing failed or empty, fallback to Map parsing");
        Map<String, Object> map = jsonSanitizer.sanitizeToMap(llmOutput);
        if (map.isEmpty()) {
            return buildFallbackResponse();
        }
        return buildFromMap(map);
    }

    /**
     * DTO 规范化：补全缺失字段、钳制评分范围
     */
    private void normalizeDto(ResumeEvaluationResponse dto) {
        if (dto.getStrengths() == null) dto.setStrengths(new ArrayList<>());
        if (dto.getWeaknesses() == null) dto.setWeaknesses(new ArrayList<>());
        if (dto.getMissingKeywords() == null) dto.setMissingKeywords(new ArrayList<>());
        if (dto.getKeywordMatches() == null) dto.setKeywordMatches(new ArrayList<>());
        if (dto.getSuggestions() == null) dto.setSuggestions(new ArrayList<>());
        if (dto.getRecommendedActions() == null) dto.setRecommendedActions(new ArrayList<>());

        dto.setScore(clampScore(dto.getScore()));
        dto.setMatchScore(clampScore(dto.getMatchScore()));
        dto.setAtsScore(clampScore(dto.getAtsScore()));
        dto.setStarScore(clampScore(dto.getStarScore()));
        dto.setCompletenessScore(clampScore(dto.getCompletenessScore()));
        // Sprint 8-C: growthAlignmentScore 规范化（默认 60 中性分）
        dto.setGrowthAlignmentScore(clampScore(dto.getGrowthAlignmentScore(), 60));

        if (dto.getSectionScores() == null) {
            dto.setSectionScores(ResumeEvaluationResponse.SectionScores.builder().build());
        }
        ResumeEvaluationResponse.SectionScores ss = dto.getSectionScores();
        ss.setSummary(clampScore(ss.getSummary()));
        ss.setExperience(clampScore(ss.getExperience()));
        ss.setProject(clampScore(ss.getProject()));
        ss.setSkills(clampScore(ss.getSkills()));
    }

    /**
     * 从 Map 构建 DTO（降级解析路径）
     */
    private ResumeEvaluationResponse buildFromMap(Map<String, Object> map) {
        ResumeEvaluationResponse.ResumeEvaluationResponseBuilder builder = ResumeEvaluationResponse.builder()
                .score(getAsInt(map, "score", 0))
                .matchScore(getAsInt(map, "matchScore", 0))
                .atsScore(getAsInt(map, "atsScore", 0))
                .starScore(getAsInt(map, "starScore", 0))
                .completenessScore(getAsInt(map, "completenessScore", 0))
                .growthAlignmentScore(getAsInt(map, "growthAlignmentScore", 60)) // Sprint8-C: 成长一致性默认60
                .strengths(toStringList(map.get("strengths")))
                .weaknesses(toStringList(map.get("weaknesses")))
                .missingKeywords(toStringList(map.get("missingKeywords")))
                .keywordMatches(parseKeywordMatches(map.get("keywordMatches")))
                .suggestions(toStringList(map.get("suggestions")))
                .recommendedActions(toStringList(map.get("recommendedActions")));

        Object ssObj = map.get("sectionScores");
        if (ssObj instanceof Map<?, ?> ssMap) {
            builder.sectionScores(ResumeEvaluationResponse.SectionScores.builder()
                    .summary(getAsInt(ssMap, "summary", 0))
                    .experience(getAsInt(ssMap, "experience", 0))
                    .project(getAsInt(ssMap, "project", 0))
                    .skills(getAsInt(ssMap, "skills", 0))
                    .build());
        } else {
            builder.sectionScores(ResumeEvaluationResponse.SectionScores.builder().build());
        }

        ResumeEvaluationResponse dto = builder.build();
        normalizeDto(dto);
        return dto;
    }

    /**
     * 解析 keywordMatches 数组
     */
    private List<ResumeEvaluationResponse.KeywordMatch> parseKeywordMatches(Object obj) {
        List<ResumeEvaluationResponse.KeywordMatch> result = new ArrayList<>();
        if (!(obj instanceof List<?> list)) return result;
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                result.add(ResumeEvaluationResponse.KeywordMatch.builder()
                        .keyword(getAsString(m, "keyword", ""))
                        .status(getAsString(m, "status", "MISSING"))
                        .evidence(getAsString(m, "evidence", ""))
                        .build());
            }
        }
        return result;
    }

    /**
     * 构建降级响应（LLM 调用失败时）
     */
    private ResumeEvaluationResponse buildFallbackResponse() {
        return ResumeEvaluationResponse.builder()
                .score(0)
                .matchScore(0)
                .atsScore(0)
                .starScore(0)
                .completenessScore(0)
                .growthAlignmentScore(0)
                .strengths(new ArrayList<>())
                .weaknesses(List.of("评估失败，请稍后重试"))
                .missingKeywords(new ArrayList<>())
                .keywordMatches(new ArrayList<>())
                .sectionScores(ResumeEvaluationResponse.SectionScores.builder().build())
                .suggestions(List.of("请稍后重试，或检查 LLM 服务状态"))
                .recommendedActions(new ArrayList<>())
                .build();
    }

    // ============================================================
    // Workflow 路由入口（实现 FocusAgent 接口）
    // ============================================================

    @Override
    public String handle(String message, Long userId, String context) {
        // ResumeEvaluatorAgent 主要通过 Service 层直接调用 evaluateResume，
        // 此处为 FocusAgent 接口兼容入口（保留扩展能力）
        log.info("ResumeEvaluatorAgent.handle called, userId={}", userId);
        return evaluateResume(message, "", null);
    }

    // ============================================================
    // 工具方法
    // ============================================================

    private Integer clampScore(Integer v) {
        if (v == null) return 0;
        if (v < 0) return 0;
        if (v > 100) return 100;
        return v;
    }

    /**
     * Sprint 8-C: 带默认值的 clampScore（growthAlignmentScore 默认 60 中性分）
     */
    private Integer clampScore(Integer v, int defaultIfNull) {
        Integer base = v == null ? defaultIfNull : v;
        return clampScore(base);
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

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
