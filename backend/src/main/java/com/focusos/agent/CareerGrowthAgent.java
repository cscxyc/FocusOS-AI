package com.focusos.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.focusos.dto.response.CareerGrowthResponse;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Sprint 8-B: Career Growth Agent — 职业成长规划专家
 * <p>
 * 职责：
 * 根据目标 JD + ResumeVersion 内容 + ResumeEvaluatorAgent 评分结果 + Personal RAG 用户画像，
 * 自动生成能力 Gap 分析、三个月成长路线、周任务计划、推荐项目实践。
 * <p>
 * 输出 JSON 格式：
 * {
 *   "currentLevel": "初级 AI 应用开发工程师",
 *   "careerGoal": "具备大厂 AI 应用开发岗位竞争力的工程师",
 *   "skillGaps": [{"skill":"","importance":"HIGH|MEDIUM|LOW","currentStatus":"","targetStatus":"","reason":""}],
 *   "roadmap": [{"month":1,"goal":"","skills":[],"tasks":[]}],
 *   "weeklyTasks": [{"week":1,"title":"","description":"","estimatedHours":8,"priority":"HIGH|MEDIUM|LOW"}],
 *   "projects": [{"name":"","purpose":"","technologies":[],"whyRecommended":""}],
 *   "summary": "..."
 * }
 * <p>
 * 技术实现（复用 Sprint 8-A 稳定化方案）：
 * - DTO + ObjectMapper + LLMJsonSanitizer 保证 JSON 100% 合法
 * - LLMCallContext (ThreadLocal) + LLMLoggingService 记录调用日志，agentType="career_growth"
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CareerGrowthAgent implements FocusAgent {

    private final ChatLanguageModel chatLanguageModel;
    private final AgentPromptProvider promptProvider;
    private final LLMJsonSanitizer jsonSanitizer;
    private final ObjectMapper objectMapper;

    @Override
    public String type() {
        return "career_growth";
    }

    // ============================================================
    // 核心方法：生成职业成长规划
    // ============================================================

    /**
     * 根据 JD + 简历内容 + 评分结果 + 用户画像生成职业成长规划
     *
     * @param jobDescription   目标 JD
     * @param resumeContent    ResumeVersion 的 Markdown 内容
     * @param evaluationScore  ResumeEvaluatorAgent 的综合评分（0-100）
     * @param evaluationJson   ResumeEvaluatorAgent 的完整评估 JSON（含 skill gaps 等）
     * @param profileContext   Personal RAG 用户画像（用于真实性核查，可为 null）
     * @return 结构化成长规划 JSON（保证 100% 合法）
     */
    /**
     * 兼容旧调用（无 Memory 上下文，等效传 memoryContext=null）
     */
    public String generateGrowthPlan(String jobDescription,
                                      String resumeContent,
                                      Integer evaluationScore,
                                      String evaluationJson,
                                      UserProfileContext profileContext) {
        return generateGrowthPlan(jobDescription, resumeContent, evaluationScore, evaluationJson, profileContext, null);
    }

    /**
     * Sprint 8-C：主方法，带 UserMemoryContext（长期成长记忆注入）
     *
     * @param memoryContext 长期记忆：用于避免重复学习已掌握技能 + 自动升级为进阶内容
     */
    public String generateGrowthPlan(String jobDescription,
                                      String resumeContent,
                                      Integer evaluationScore,
                                      String evaluationJson,
                                      UserProfileContext profileContext,
                                      UserMemoryContext memoryContext) {
        boolean profileEmpty = profileContext == null
                || !profileContext.isRetrievalSuccess()
                || (profileContext.getProfileText() == null || profileContext.getProfileText().isBlank());
        String profileText = profileEmpty
                ? "（Personal RAG 检索为空或失败，无法进行真实性核查）"
                : profileContext.renderWithSources();

        // Sprint 8-C：长期记忆块（通用记忆展示 + 避免重复学习进阶约束）
        String memorySection;
        if (memoryContext != null) {
            memorySection = memoryContext.renderAsPromptSection()
                    + "\n"
                    + memoryContext.renderSkillHintsForCareerGrowth();
        } else {
            memorySection = "【长期成长记忆】（未加载，将按常规成长路径安排）\n";
        }

        int score = evaluationScore == null ? 0 : evaluationScore;

        String prompt = String.format("""
                请根据以下信息为候选人生成真实可执行的三个月职业成长规划。

                【目标职位 JD】
                %s

                【候选人简历内容（Markdown）】
                %s

                【候选人当前简历评分（ResumeEvaluatorAgent 评估结果）】
                综合评分：%d / 100（评分等级：55-65 为初级、66-80 为中级、81+ 为高级）

                【候选人完整评估明细 JSON（含 skill gaps / missing keywords 等）】
                %s

                【候选人 Personal RAG 真实资料（用于真实性核查）】
                %s

                %s

                %s

                严格按以下 JSON 格式输出（不要输出任何其他内容，不要使用 Markdown 代码块）：
                {
                  "currentLevel": "当前能力等级定位（如：初级 AI 应用开发工程师，基于评分 55-65=初级、66-80=中级、81+=高级）",
                  "careerGoal": "职业目标（如：3个月内具备字节跳动 AI 应用开发岗位竞争力的工程师，必须与目标 JD 一致）",
                  "skillGaps": [
                    {
                      "skill": "技能名称（如：Spring Cloud）",
                      "importance": "HIGH | MEDIUM | LOW",
                      "currentStatus": "当前状态（必须基于简历或 Personal RAG，如：未接触 / 了解概念 / 有基础实践）",
                      "targetStatus": "目标状态（如：能独立设计微服务架构）",
                      "reason": "提升原因（必须明确引用 JD 中的某条要求，禁止泛泛而谈）"
                    }
                  ],
                  "roadmap": [
                    {
                      "month": 1,
                      "goal": "第 1 个月目标（基础补齐）",
                      "skills": ["技能 1", "技能 2"],
                      "tasks": ["任务 1", "任务 2"]
                    },
                    {
                      "month": 2,
                      "goal": "第 2 个月目标（进阶实践）",
                      "skills": ["技能 3", "技能 4"],
                      "tasks": ["任务 3", "任务 4"]
                    },
                    {
                      "month": 3,
                      "goal": "第 3 个月目标（项目落地）",
                      "skills": ["技能 5", "技能 6"],
                      "tasks": ["任务 5", "任务 6"]
                    }
                  ],
                  "weeklyTasks": [
                    {
                      "week": 1,
                      "title": "任务标题（具体可执行）",
                      "description": "任务详细描述",
                      "estimatedHours": 8,
                      "priority": "HIGH | MEDIUM | LOW"
                    }
                  ],
                  "projects": [
                    {
                      "name": "推荐项目名称",
                      "purpose": "项目目的（要解决什么问题、练习什么能力）",
                      "technologies": ["技术栈 1", "技术栈 2"],
                      "whyRecommended": "推荐理由（说明该项目能补齐哪个 skillGap，必须对应 JD 要求）"
                    }
                  ],
                  "summary": "总结（3-5 句话，核心路径 + 预期成果 + 鼓励）"
                }

                【规划要求 — 必须严格遵守】
                1. currentLevel 必须基于评分定位：评分 55-65=初级、66-80=中级、81+=高级、<55=入门级。
                2. careerGoal 必须与目标 JD 一致，明确时间范围（3 个月）和目标公司/岗位类型。
                3. skillGaps 至少 3 条，每条的 reason 必须明确引用 JD 中的具体要求（如"JD 要求 Spring Cloud 微服务架构能力"），禁止泛泛而谈（如"提升编程能力"不算具体 Gap）。
                4. roadmap 必须为 3 个月（month 1/2/3），每月必须包含 goal / skills（2-4 个）/ tasks（2-4 个），按月递进：month 1 打基础、month 2 进阶实践、month 3 项目落地。
                5. weeklyTasks 必须为 8-12 个任务，覆盖 12 周，每个任务必须有 title / description / estimatedHours（2-20 小时）/ priority，任务必须具体可执行（禁止"学习 Java"这类宽泛建议，必须是"完成 Spring Cloud Gateway 路由配置实践"这类具体任务）。
                6. projects 必须为 2-3 个推荐项目，每个项目必须对应 JD 中的具体技术要求，whyRecommended 必须说明该项目能补齐哪个 skillGap（如"补齐 JD 要求的微服务与消息队列能力 Gap"）。
                7. 不能编造用户经历：所有 currentStatus 必须基于简历内容或 Personal RAG 真实资料，禁止臆测用户未提及的能力；如简历未提及某技能，currentStatus 必须是"未接触"或"简历未提及"。
                8. 所有学习建议必须结合行业趋势和目标岗位的实际技术栈，避免过时技术（如不要建议学习 EJB、Struts 等）。
                9. summary 必须总结规划的核心路径和预期成果，3-5 句话，语气专业且鼓励。
                10. 资料不足时（Personal RAG 为空）：仍可基于简历内容规划，但 skillGaps 中的 currentStatus 必须标注"简历未提及，建议个人确认"，且 roadmap 不宜过于激进。
                11. 【JSON 格式严格要求】在 JSON 字符串值内部，禁止使用未转义的双引号（"）引用文本。如需引用 JD 或简历原文，请使用单引号（'）或书名号（《》）代替。例如：正确写法 "JD 要求'高并发'经验"，错误写法 "JD 要求"高并发"经验"。
                12. 【重要】禁止输出 Markdown 代码块（如 ```json ... ```），只输出纯 JSON 字符串。
                """,
                isBlank(jobDescription) ? "（未提供 JD，将基于简历内容做通用规划）" : truncate(jobDescription, 4000),
                isBlank(resumeContent) ? "（简历内容为空，将基于评分结果做通用规划）" : truncate(resumeContent, 6000),
                score,
                isBlank(evaluationJson) ? "（无评估明细，将基于评分结果做通用规划）" : truncate(evaluationJson, 4000),
                profileText,
                memorySection,
                profileEmpty ? "【警告】Personal RAG 资料不足，无法进行真实性核查，所有 currentStatus 请标注'简历未提及，建议个人确认'，规划不宜过于激进。" : "");

        String fullPrompt = promptProvider.careerGrowthSystemPrompt() + "\n\n" + prompt;

        try {
            String response = chatLanguageModel.chat(fullPrompt);
            // Sprint 8-B: 通过 LLMJsonSanitizer 清洗 + 解析为 DTO + 序列化保证 JSON 稳定性
            CareerGrowthResponse dto = parseGrowthResponse(response);

            // Sprint 8-B: 重试机制 — 若首次解析失败，追加 JSON 格式提醒后重试一次
            if (dto == null || dto.getCurrentLevel() == null) {
                log.warn("First career growth parse failed, retrying with JSON format reminder...");
                String retryPrompt = fullPrompt + "\n\n【重要提醒】你上次的输出包含 JSON 格式错误（字符串值内部有未转义的双引号或使用了 Markdown 代码块包裹）。请重新输出严格合法的 JSON。在字符串值内部，禁止使用双引号，请用单引号代替。绝对不要输出 ```json 代码块包裹，只输出纯 JSON。";
                String retryResponse = chatLanguageModel.chat(retryPrompt);
                dto = parseGrowthResponse(retryResponse);
            }

            return jsonSanitizer.serialize(dto);
        } catch (Exception e) {
            log.error("Failed to generate career growth plan", e);
            CareerGrowthResponse fallback = buildFallbackResponse();
            return jsonSanitizer.serialize(fallback);
        }
    }

    // ============================================================
    // DTO 解析（含 Schema 校验 + 降级 Map 解析）
    // ============================================================

    /**
     * 解析 LLM 规划输出为 DTO（复用 Sprint 8-A 方案）
     * <p>
     * 流程：
     * 1. LLMJsonSanitizer 清洗原始输出
     * 2. ObjectMapper 解析为 CareerGrowthResponse DTO
     * 3. Schema 校验：补全缺失字段，规范化枚举值
     * 4. 若解析失败：降级为手动 Map 解析 + 字段补全
     */
    private CareerGrowthResponse parseGrowthResponse(String llmOutput) {
        // 1. 清洗 + 解析为 DTO
        CareerGrowthResponse dto = jsonSanitizer.sanitizeToObject(llmOutput, CareerGrowthResponse.class);
        if (dto == null || dto.getCurrentLevel() == null) {
            log.warn("CareerGrowth DTO parsing failed. Full LLM output (len={}):\n{}", llmOutput.length(), llmOutput);
        }
        if (dto != null && dto.getCurrentLevel() != null) {
            normalizeDto(dto);
            return dto;
        }

        // 2. 降级：手动 Map 解析
        log.warn("CareerGrowth DTO parsing failed or empty, fallback to Map parsing");
        Map<String, Object> map = jsonSanitizer.sanitizeToMap(llmOutput);
        if (map.isEmpty()) {
            return buildFallbackResponse();
        }
        return buildFromMap(map);
    }

    /**
     * DTO 规范化：补全缺失字段、规范化枚举、约束数组大小
     */
    private void normalizeDto(CareerGrowthResponse dto) {
        if (dto.getSkillGaps() == null) dto.setSkillGaps(new ArrayList<>());
        if (dto.getRoadmap() == null) dto.setRoadmap(new ArrayList<>());
        if (dto.getWeeklyTasks() == null) dto.setWeeklyTasks(new ArrayList<>());
        if (dto.getProjects() == null) dto.setProjects(new ArrayList<>());

        // 规范化 importance / priority
        for (CareerGrowthResponse.SkillGap gap : dto.getSkillGaps()) {
            if (gap.getImportance() == null) gap.setImportance("MEDIUM");
            String imp = gap.getImportance().toUpperCase();
            if (!List.of("HIGH", "MEDIUM", "LOW").contains(imp)) imp = "MEDIUM";
            gap.setImportance(imp);
        }
        for (CareerGrowthResponse.WeeklyTask task : dto.getWeeklyTasks()) {
            if (task.getPriority() == null) task.setPriority("MEDIUM");
            String pr = task.getPriority().toUpperCase();
            if (!List.of("HIGH", "MEDIUM", "LOW").contains(pr)) pr = "MEDIUM";
            task.setPriority(pr);
            if (task.getEstimatedHours() == null) task.setEstimatedHours(8);
            if (task.getEstimatedHours() < 1) task.setEstimatedHours(1);
            if (task.getEstimatedHours() > 40) task.setEstimatedHours(40);
            if (task.getWeek() == null) task.setWeek(1);
            if (task.getWeek() < 1) task.setWeek(1);
            if (task.getWeek() > 12) task.setWeek(12);
        }
        for (CareerGrowthResponse.LearningStage stage : dto.getRoadmap()) {
            if (stage.getSkills() == null) stage.setSkills(new ArrayList<>());
            if (stage.getTasks() == null) stage.setTasks(new ArrayList<>());
            if (stage.getMonth() == null) stage.setMonth(1);
        }
        for (CareerGrowthResponse.ProjectRecommendation project : dto.getProjects()) {
            if (project.getTechnologies() == null) project.setTechnologies(new ArrayList<>());
        }
    }

    /**
     * 从 Map 构建 DTO（降级解析路径）
     */
    private CareerGrowthResponse buildFromMap(Map<String, Object> map) {
        CareerGrowthResponse.CareerGrowthResponseBuilder builder = CareerGrowthResponse.builder()
                .currentLevel(getAsString(map, "currentLevel", "入门级工程师"))
                .careerGoal(getAsString(map, "careerGoal", "3个月内提升简历竞争力"))
                .skillGaps(parseSkillGaps(map.get("skillGaps")))
                .roadmap(parseRoadmap(map.get("roadmap")))
                .weeklyTasks(parseWeeklyTasks(map.get("weeklyTasks")))
                .projects(parseProjects(map.get("projects")))
                .summary(getAsString(map, "summary", ""));

        CareerGrowthResponse dto = builder.build();
        normalizeDto(dto);
        return dto;
    }

    private List<CareerGrowthResponse.SkillGap> parseSkillGaps(Object obj) {
        List<CareerGrowthResponse.SkillGap> result = new ArrayList<>();
        if (!(obj instanceof List<?> list)) return result;
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                result.add(CareerGrowthResponse.SkillGap.builder()
                        .skill(getAsString(m, "skill", ""))
                        .importance(getAsString(m, "importance", "MEDIUM"))
                        .currentStatus(getAsString(m, "currentStatus", "简历未提及"))
                        .targetStatus(getAsString(m, "targetStatus", ""))
                        .reason(getAsString(m, "reason", ""))
                        .build());
            }
        }
        return result;
    }

    private List<CareerGrowthResponse.LearningStage> parseRoadmap(Object obj) {
        List<CareerGrowthResponse.LearningStage> result = new ArrayList<>();
        if (!(obj instanceof List<?> list)) return result;
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                result.add(CareerGrowthResponse.LearningStage.builder()
                        .month(getAsInt(m, "month", 1))
                        .goal(getAsString(m, "goal", ""))
                        .skills(toStringList(m.get("skills")))
                        .tasks(toStringList(m.get("tasks")))
                        .build());
            }
        }
        return result;
    }

    private List<CareerGrowthResponse.WeeklyTask> parseWeeklyTasks(Object obj) {
        List<CareerGrowthResponse.WeeklyTask> result = new ArrayList<>();
        if (!(obj instanceof List<?> list)) return result;
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                result.add(CareerGrowthResponse.WeeklyTask.builder()
                        .week(getAsInt(m, "week", 1))
                        .title(getAsString(m, "title", ""))
                        .description(getAsString(m, "description", ""))
                        .estimatedHours(getAsInt(m, "estimatedHours", 8))
                        .priority(getAsString(m, "priority", "MEDIUM"))
                        .build());
            }
        }
        return result;
    }

    private List<CareerGrowthResponse.ProjectRecommendation> parseProjects(Object obj) {
        List<CareerGrowthResponse.ProjectRecommendation> result = new ArrayList<>();
        if (!(obj instanceof List<?> list)) return result;
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                result.add(CareerGrowthResponse.ProjectRecommendation.builder()
                        .name(getAsString(m, "name", ""))
                        .purpose(getAsString(m, "purpose", ""))
                        .technologies(toStringList(m.get("technologies")))
                        .whyRecommended(getAsString(m, "whyRecommended", ""))
                        .build());
            }
        }
        return result;
    }

    /**
     * 构建降级响应（LLM 调用失败时）
     */
    private CareerGrowthResponse buildFallbackResponse() {
        return CareerGrowthResponse.builder()
                .currentLevel("入门级工程师")
                .careerGoal("3个月内提升简历竞争力")
                .skillGaps(List.of(
                        CareerGrowthResponse.SkillGap.builder()
                                .skill("分布式系统设计")
                                .importance("HIGH")
                                .currentStatus("规划生成失败，建议重新生成")
                                .targetStatus("能独立设计分布式系统")
                                .reason("目标岗位通常要求分布式能力")
                                .build()
                ))
                .roadmap(List.of(
                        CareerGrowthResponse.LearningStage.builder()
                                .month(1).goal("基础补齐").skills(List.of("Java 基础", "Spring Boot")).tasks(List.of("重新生成规划后查看详细任务")).build(),
                        CareerGrowthResponse.LearningStage.builder()
                                .month(2).goal("进阶实践").skills(List.of("MySQL", "Redis")).tasks(List.of("重新生成规划后查看详细任务")).build(),
                        CareerGrowthResponse.LearningStage.builder()
                                .month(3).goal("项目落地").skills(List.of("RAG", "Agent")).tasks(List.of("重新生成规划后查看详细任务")).build()
                ))
                .weeklyTasks(List.of(
                        CareerGrowthResponse.WeeklyTask.builder()
                                .week(1).title("规划生成失败，建议重新生成")
                                .description("请检查 LLM 服务状态后重新生成职业成长规划")
                                .estimatedHours(1).priority("HIGH").build()
                ))
                .projects(List.of(
                        CareerGrowthResponse.ProjectRecommendation.builder()
                                .name("规划生成失败，建议重新生成")
                                .purpose("请重新生成后查看推荐项目")
                                .technologies(List.of("N/A"))
                                .whyRecommended("LLM 服务可能暂时不可用").build()
                ))
                .summary("规划生成失败，请检查 LLM 服务状态后重新生成职业成长规划。")
                .build();
    }

    // ============================================================
    // Workflow 路由入口（实现 FocusAgent 接口）
    // ============================================================

    @Override
    public String handle(String message, Long userId, String context) {
        // CareerGrowthAgent 主要通过 Service 层直接调用 generateGrowthPlan，
        // 此处为 FocusAgent 接口兼容入口（保留扩展能力）
        log.info("CareerGrowthAgent.handle called, userId={}", userId);
        return generateGrowthPlan(message, "", 50, "", null);
    }

    // ============================================================
    // 工具方法
    // ============================================================

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
