package com.focusos.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.focusos.agent.WorkflowEventBus.WorkflowEvent;
import com.focusos.config.WorkflowExecutorConfig;
import com.focusos.dto.response.WorkflowResponse;
import com.focusos.entity.AgentTask;
import com.focusos.entity.CareerAnalysisReport;
import com.focusos.entity.InterviewSession;
import com.focusos.repository.AgentTaskRepository;
import com.focusos.repository.CareerAnalysisReportRepository;
import com.focusos.repository.InterviewSessionRepository;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Sprint 6-A: 同步 Multi-Agent Workflow Service
 * Sprint 6-B: 升级为异步执行 + WorkflowContext 共享上下文 + SSE 事件推送
 * <p>
 * 核心能力：
 * 1. 任务规划（LLM 拆解用户目标为多个 AgentTask）
 * 2. WorkflowContext 初始化（Personal RAG 一次性检索，避免 Agent 重复 embedding）
 * 3. 异步任务执行（按依赖关系调度，@Async 线程池隔离）
 * 4. SSE 事件实时推送（task_started / task_completed / workflow_completed）
 * 5. 结果汇总（MasterAgent 综合各 Agent 输出生成最终建议）
 */
@Slf4j
@Service
public class AgentWorkflowService {

    private final ChatLanguageModel chatLanguageModel;
    private final AgentRegistry agentRegistry;
    private final AgentTaskRepository taskRepository;
    private final AgentPromptProvider promptProvider;
    private final ObjectMapper objectMapper;
    private final WorkflowEventBus eventBus;
    private final RAGAgent ragAgent;
    private final PersonalProfileService personalProfileService;
    private final CareerAnalysisReportRepository careerReportRepository;
    private final InterviewSessionRepository interviewSessionRepository;
    private final Executor workflowExecutor;

    public AgentWorkflowService(ChatLanguageModel chatLanguageModel,
                                AgentRegistry agentRegistry,
                                AgentTaskRepository taskRepository,
                                AgentPromptProvider promptProvider,
                                ObjectMapper objectMapper,
                                WorkflowEventBus eventBus,
                                RAGAgent ragAgent,
                                PersonalProfileService personalProfileService,
                                CareerAnalysisReportRepository careerReportRepository,
                                InterviewSessionRepository interviewSessionRepository,
                                @Qualifier(WorkflowExecutorConfig.WORKFLOW_EXECUTOR) Executor workflowExecutor) {
        this.chatLanguageModel = chatLanguageModel;
        this.agentRegistry = agentRegistry;
        this.taskRepository = taskRepository;
        this.promptProvider = promptProvider;
        this.objectMapper = objectMapper;
        this.eventBus = eventBus;
        this.ragAgent = ragAgent;
        this.personalProfileService = personalProfileService;
        this.careerReportRepository = careerReportRepository;
        this.interviewSessionRepository = interviewSessionRepository;
        this.workflowExecutor = workflowExecutor;
    }

    // ============================================================
    // Sprint 6-B: 异步 Workflow 入口
    // ============================================================

    /**
     * Sprint 6-B: 启动异步 Workflow（立即返回 workflowId，所有 LLM 调用都在异步线程中执行）
     * <p>
     * 调用链：Controller → 此方法（同步，仅生成 ID + 发布 started 事件）→ @Async 异步执行全部流程
     */
    public String startWorkflowAsync(Long userId, String userGoal) {
        String workflowId = UUID.randomUUID().toString().substring(0, 8);
        log.info("===== Sprint 6-B Async Workflow Start =====");
        log.info("WorkflowId={}, userId={}, goal={}", workflowId, userId, userGoal);

        // 同步阶段：仅发布 workflow_started 事件（无 LLM 调用，毫秒级）
        eventBus.publish(workflowId, WorkflowEvent.builder()
                .event("workflow_started")
                .workflowId(workflowId)
                .message("开始分析目标: " + userGoal)
                .totalTasks(0)
                .completedTasks(0)
                .progress(0)
                .timestamp(now())
                .build());

        // 异步执行全部流程（使用 CompletableFuture + 显式 Executor，避免 @Async 自调用失效）
        final Long userIdFinal = userId;
        final String workflowIdFinal = workflowId;
        final String userGoalFinal = userGoal;
        CompletableFuture.runAsync(
                () -> executeWorkflowAsyncInternal(userIdFinal, workflowIdFinal, userGoalFinal),
                workflowExecutor
        );

        return workflowId;
    }

    // ============================================================
    // Sprint 7-A: Career Workflow（5步 DAG，固定任务编排）
    // ============================================================

    /**
     * Sprint 7-A: 启动 Career Workflow — AI 求职闭环
     * Sprint 7-B: 升级为 6 步 DAG，新增 Task6 MOCK_INTERVIEW
     * <p>
     * 固定 6 步 DAG（不依赖 LLM 规划）：
     * Task1: CAREER_ANALYSIS (career)         — 无依赖
     * Task2: RESUME_OPTIMIZATION (resume-opt) — 依赖 Task1
     * Task3: SKILL_GAP_ANALYSIS (career)      — 依赖 Task1（与 Task2 并行）
     * Task4: LEARNING_PLAN (learning)         — 依赖 Task3
     * Task5: INTERVIEW_PREPARATION (interview)— 依赖 Task1（与 Task2/Task3 并行）
     * Task6: MOCK_INTERVIEW (interview)       — 依赖 Task5（生成初始面试题）
     * <p>
     * DAG 结构：
     *          Task1
     *         / | \
     *      T2  T3  T5
     *         |    |
     *        T4    T6
     * <p>
     * 完成后聚合结果保存到 CareerAnalysisReport，并联动创建 InterviewSession。
     */
    public String startCareerWorkflowAsync(Long userId, String jobDescription,
                                            String jobTitle, String company) {
        String workflowId = "career-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("===== Sprint 7-B Career Workflow Start =====");
        log.info("WorkflowId={}, userId={}, jobTitle={}, jdLength={}",
                workflowId, userId, jobTitle, jobDescription != null ? jobDescription.length() : 0);

        eventBus.publish(workflowId, WorkflowEvent.builder()
                .event("workflow_started")
                .workflowId(workflowId)
                .message("开始 Career 分析: " + (jobTitle != null ? jobTitle : "目标岗位"))
                .totalTasks(6)
                .completedTasks(0)
                .progress(0)
                .timestamp(now())
                .build());

        final Long userIdF = userId;
        final String workflowIdF = workflowId;
        final String jdF = jobDescription;
        final String titleF = jobTitle;
        final String companyF = company;

        CompletableFuture.runAsync(
                () -> executeCareerWorkflowAsyncInternal(userIdF, workflowIdF, jdF, titleF, companyF),
                workflowExecutor
        );

        return workflowId;
    }

    /**
     * Career Workflow 异步执行内部方法
     */
    private void executeCareerWorkflowAsyncInternal(Long userId, String workflowId,
                                                     String jobDescription, String jobTitle, String company) {
        long workflowStart = System.currentTimeMillis();
        log.info("[Career Workflow] {} started in thread {}", workflowId, Thread.currentThread().getName());

        // Sprint 7-C-B: 为整个 Career Workflow 设置 LLM 调用上下文（覆盖初始 RAG 检索 + 结果汇总）
        LLMCallContext.set(userId, workflowId, "career_workflow");

        try {
            // 1. 初始化 Career 专用 WorkflowContext（仅检索 CAREER/EXPERIENCE/PROJECT）
            eventBus.publish(workflowId, WorkflowEvent.builder()
                    .event("task_started")
                    .workflowId(workflowId)
                    .agentType("rag")
                    .task("检索个人知识库（简历/项目/实习）")
                    .taskType("CONTEXT_INIT")
                    .status("RUNNING")
                    .message("正在检索用户简历、项目经历、实习经历...")
                    .progress(5)
                    .timestamp(now())
                    .build());

            UserProfileContext profileContext = personalProfileService.retrieveCareerProfile(userId, jobDescription);
            WorkflowContext context = WorkflowContext.builder()
                    .userId(userId)
                    .workflowId(workflowId)
                    .userGoal("Career Analysis: " + (jobTitle != null ? jobTitle : "目标岗位"))
                    .userProfile(profileContext.getProfileText())
                    .skills(profileContext.getSkills() != null
                            ? profileContext.getSkills().stream().map(UserProfileContext.SourcedSkill::getValue).toList()
                            : List.of())
                    .projects(List.of())
                    .experiences(List.of())
                    .ragRetrievalSuccess(profileContext.isRetrievalSuccess())
                    .ragRetrievalError(profileContext.getRetrievalError())
                    .userProfileContext(profileContext)
                    .createdAtMillis(System.currentTimeMillis())
                    .build();

            log.info("[Career Workflow] {} context: ragSuccess={}, profileLength={}, sourceDocs={}",
                    workflowId, context.isRagRetrievalSuccess(),
                    context.getUserProfile() != null ? context.getUserProfile().length() : 0,
                    profileContext.getSourceDocuments() != null ? profileContext.getSourceDocuments().size() : 0);

            eventBus.publish(workflowId, WorkflowEvent.builder()
                    .event("task_completed")
                    .workflowId(workflowId)
                    .agentType("rag")
                    .taskType("CONTEXT_INIT")
                    .status(context.isRagRetrievalSuccess() ? "SUCCESS" : "DEGRADED")
                    .message(context.isRagRetrievalSuccess()
                            ? "个人知识库检索完成（" + context.getUserProfile().length() + " 字符）"
                            : "RAG 检索失败，降级为通用上下文")
                    .progress(10)
                    .timestamp(now())
                    .build());

            // 2. 创建 5 步 DAG 任务（固定编排，不依赖 LLM 规划）
            List<AgentTask> tasks = createCareerWorkflowTasks(userId, workflowId, jobDescription, jobTitle, company);
            log.info("[Career Workflow] {} created {} tasks", workflowId, tasks.size());

            // 3. DAG 并行执行
            executeTasksDAG(userId, workflowId, tasks, context);

            // 4. 聚合结果保存到 CareerAnalysisReport
            CareerAnalysisReport report = aggregateCareerReport(userId, workflowId, jobDescription, jobTitle, company, tasks, context);
            careerReportRepository.save(report);
            log.info("[Career Workflow] {} report saved: id={}, matchScore={}",
                    workflowId, report.getId(), report.getMatchScore());

            // 5. 发布 workflow_completed
            long totalDuration = System.currentTimeMillis() - workflowStart;
            long successCount = tasks.stream().filter(t -> "SUCCESS".equals(t.getStatus())).count();

            eventBus.publish(workflowId, WorkflowEvent.builder()
                    .event("workflow_completed")
                    .workflowId(workflowId)
                    .status(successCount == tasks.size() ? "SUCCESS" : "PARTIAL")
                    .message(String.format("Career 分析完成: %d/%d 任务成功, 匹配度 %d%%, 耗时 %dms",
                            successCount, tasks.size(),
                            report.getMatchScore() != null ? report.getMatchScore() : 0, totalDuration))
                    .summary(report.getOverallRecommendation())
                    .totalTasks(tasks.size())
                    .completedTasks((int) successCount)
                    .progress(100)
                    .durationMs(totalDuration)
                    .timestamp(now())
                    .build());

            log.info("[Career Workflow] {} done: {}/{} success, {}ms, matchScore={}",
                    workflowId, successCount, tasks.size(), totalDuration, report.getMatchScore());

        } catch (Exception e) {
            log.error("[Career Workflow] {} failed unexpectedly", workflowId, e);
            eventBus.publish(workflowId, WorkflowEvent.builder()
                    .event("workflow_failed")
                    .workflowId(workflowId)
                    .status("FAILED")
                    .message("Career 工作流异常: " + e.getMessage())
                    .errorMessage(e.getMessage())
                    .timestamp(now())
                    .build());
        } finally {
            eventBus.complete(workflowId);
            LLMCallContext.clear();  // Sprint 7-C-B: 清理 LLM 调用上下文
        }
    }

    /**
     * 创建 Career Workflow 的 6 步固定 DAG 任务（Sprint 7-B）
     * <p>
     * DAG 依赖：
     * Task1(CAREER_ANALYSIS) → 无依赖
     * Task2(RESUME_OPTIMIZATION) → 依赖 Task1
     * Task3(SKILL_GAP_ANALYSIS) → 依赖 Task1
     * Task4(LEARNING_PLAN) → 依赖 Task3
     * Task5(INTERVIEW_PREPARATION, interview) → 依赖 Task1
     * Task6(MOCK_INTERVIEW, interview) → 依赖 Task5
     */
    private List<AgentTask> createCareerWorkflowTasks(Long userId, String workflowId,
                                                       String jobDescription, String jobTitle, String company) {
        String jdSummary = jobDescription != null ? jobDescription : "未提供 JD";
        String titleStr = jobTitle != null ? jobTitle : "目标岗位";

        List<AgentTask> tasks = new ArrayList<>();

        // Task1: CAREER_ANALYSIS（无依赖）
        AgentTask task1 = new AgentTask();
        task1.setUserId(userId);
        task1.setWorkflowId(workflowId);
        task1.setGoal("分析岗位匹配度: " + titleStr);
        task1.setTaskType("CAREER_ANALYSIS");
        task1.setAgentType("career");
        task1.setInputParams("职位描述: " + truncate(jdSummary, 2000) + "\n岗位: " + titleStr + "\n公司: " + (company != null ? company : "未知"));
        task1.setStatus("PLANNING");
        task1 = taskRepository.save(task1);
        tasks.add(task1);

        // Task2: RESUME_OPTIMIZATION（依赖 Task1）
        AgentTask task2 = new AgentTask();
        task2.setUserId(userId);
        task2.setWorkflowId(workflowId);
        task2.setGoal("基于 STAR 原则优化简历，突出与目标岗位匹配的经历");
        task2.setTaskType("RESUME_OPTIMIZATION");
        task2.setAgentType("resume-optimization");
        task2.setDependsOn(task1.getId());
        task2.setInputParams("基于岗位匹配分析结果，优化简历");
        task2.setStatus("PLANNING");
        task2 = taskRepository.save(task2);
        tasks.add(task2);

        // Task3: SKILL_GAP_ANALYSIS（依赖 Task1，与 Task2 并行）
        AgentTask task3 = new AgentTask();
        task3.setUserId(userId);
        task3.setWorkflowId(workflowId);
        task3.setGoal("分析技能差距：对比 JD 要求和用户实际技能");
        task3.setTaskType("SKILL_GAP_ANALYSIS");
        task3.setAgentType("career");
        task3.setDependsOn(task1.getId());
        task3.setInputParams("基于岗位匹配分析，深入分析技能差距");
        task3.setStatus("PLANNING");
        task3 = taskRepository.save(task3);
        tasks.add(task3);

        // Task4: LEARNING_PLAN（依赖 Task3）
        AgentTask task4 = new AgentTask();
        task4.setUserId(userId);
        task4.setWorkflowId(workflowId);
        task4.setGoal("生成 12 周技能补齐学习计划");
        task4.setTaskType("LEARNING_PLAN");
        task4.setAgentType("learning");
        task4.setDependsOn(task3.getId());
        task4.setInputParams("基于技能差距分析，生成 12 周学习计划");
        task4.setStatus("PLANNING");
        task4 = taskRepository.save(task4);
        tasks.add(task4);

        // Task5: INTERVIEW_PREPARATION（依赖 Task1，与 Task2/Task3 并行）— Sprint 7-B 改为 interview agent
        AgentTask task5 = new AgentTask();
        task5.setUserId(userId);
        task5.setWorkflowId(workflowId);
        task5.setGoal("生成面试题：覆盖 Java/Spring Boot/AI应用/RAG/Agent/项目深挖 6 大类别");
        task5.setTaskType("INTERVIEW_PREPARATION");
        task5.setAgentType("interview");
        task5.setDependsOn(task1.getId());
        task5.setInputParams("基于岗位匹配分析，生成定制化面试题（必须结合用户真实经历）");
        task5.setStatus("PLANNING");
        task5 = taskRepository.save(task5);
        tasks.add(task5);

        // Task6: MOCK_INTERVIEW（依赖 Task5）— Sprint 7-B 新增
        AgentTask task6 = new AgentTask();
        task6.setUserId(userId);
        task6.setWorkflowId(workflowId);
        task6.setGoal("初始化模拟面试会话：复用 Task5 面试题，创建 InterviewSession 供用户后续对话");
        task6.setTaskType("MOCK_INTERVIEW");
        task6.setAgentType("interview");
        task6.setDependsOn(task5.getId());
        task6.setInputParams("基于面试题初始化模拟面试会话");
        task6.setStatus("PLANNING");
        task6 = taskRepository.save(task6);
        tasks.add(task6);

        log.info("Created 6 Career Workflow tasks for workflowId={}: DAG Task1→[Task2,Task3,Task5], Task3→Task4, Task5→Task6",
                workflowId);
        return tasks;
    }

    /**
     * 聚合 6 步任务结果到 CareerAnalysisReport（Sprint 7-B 升级）
     * 同时联动创建 InterviewSession（持久化 Task5/Task6 的面试题）
     */
    private CareerAnalysisReport aggregateCareerReport(Long userId, String workflowId,
                                                        String jobDescription, String jobTitle, String company,
                                                        List<AgentTask> tasks, WorkflowContext context) {
        CareerAnalysisReport report = new CareerAnalysisReport();
        report.setUserId(userId);
        report.setWorkflowId(workflowId);
        report.setJobTitle(jobTitle);
        report.setCompany(company);
        report.setJobDescription(jobDescription);

        String interviewQuestionsJson = null;  // 收集 Task5 面试题，用于创建 InterviewSession

        // 从各任务结果中提取字段
        for (AgentTask task : tasks) {
            if (!"SUCCESS".equals(task.getStatus()) || task.getResult() == null) continue;

            try {
                String result = task.getResult().trim();
                switch (task.getTaskType()) {
                    case "CAREER_ANALYSIS" -> {
                        Map<String, Object> careerResult = parseJsonToMap(result);
                        report.setMatchScore(extractInt(careerResult.get("matchScore")));
                        report.setCandidateProfile(extractString(careerResult.get("candidateProfile")));
                        report.setAdvantages(toJsonString(careerResult.get("advantages")));
                        report.setGaps(toJsonString(careerResult.get("gaps")));
                    }
                    case "RESUME_OPTIMIZATION" -> {
                        report.setResumeSuggestions(result);
                    }
                    case "LEARNING_PLAN" -> {
                        report.setLearningPlan(result);
                    }
                    case "INTERVIEW_PREPARATION" -> {
                        report.setInterviewQuestions(result);
                        interviewQuestionsJson = result;  // 保存用于 InterviewSession
                    }
                    case "MOCK_INTERVIEW" -> {
                        // Task6 复用 Task5 面试题，结果可能相同；若不同则覆盖
                        if (interviewQuestionsJson == null) {
                            interviewQuestionsJson = result;
                            report.setInterviewQuestions(result);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse task result for {}: type={}", task.getId(), task.getTaskType(), e);
            }
        }

        // 资料是否充足
        boolean profileSufficient = context.isRagRetrievalSuccess()
                && context.getUserProfile() != null
                && context.getUserProfile().length() > 50;
        report.setProfileSufficient(profileSufficient);

        // 生成整体建议
        report.setOverallRecommendation(generateOverallRecommendation(report, tasks));

        // Sprint 7-B: 联动创建 InterviewSession（如果有面试题）
        if (interviewQuestionsJson != null) {
            try {
                createInterviewSessionFromWorkflow(userId, workflowId, jobDescription, jobTitle, company,
                        interviewQuestionsJson, profileSufficient);
            } catch (Exception e) {
                log.warn("Failed to create InterviewSession for workflow {}", workflowId, e);
            }
        }

        return report;
    }

    /**
     * Sprint 7-B: Career Workflow 完成后联动创建 InterviewSession
     * 用户可在 Interview Tab 中查看面试题并开始模拟面试对话
     */
    private void createInterviewSessionFromWorkflow(Long userId, String workflowId,
                                                     String jobDescription, String jobTitle, String company,
                                                     String questionsJson, boolean profileSufficient) {
        // 避免重复创建（同一 workflow 只创建一次）
        if (interviewSessionRepository.findByWorkflowId(workflowId).isPresent()) {
            log.info("InterviewSession already exists for workflow {}, skip", workflowId);
            return;
        }
        InterviewSession session = new InterviewSession();
        session.setUserId(userId);
        session.setWorkflowId(workflowId);
        session.setJobTitle(jobTitle);
        session.setCompany(company);
        session.setJobDescription(jobDescription);
        session.setQuestionsJson(questionsJson);
        session.setConversationJson("[]");
        session.setStatus("IN_PROGRESS");
        session.setAnsweredCount(0);
        session.setProfileSufficient(profileSufficient);
        InterviewSession saved = interviewSessionRepository.save(session);
        log.info("[Career Workflow] {} created InterviewSession id={} for user {}",
                workflowId, saved.getId(), userId);
    }

    /**
     * 生成整体建议摘要
     */
    private String generateOverallRecommendation(CareerAnalysisReport report, List<AgentTask> tasks) {
        int score = report.getMatchScore() != null ? report.getMatchScore() : 0;
        String level = score >= 80 ? "高度匹配" : score >= 60 ? "良好匹配" : score >= 40 ? "部分匹配" : "需要提升";
        long successCount = tasks.stream().filter(t -> "SUCCESS".equals(t.getStatus())).count();

        return String.format("""
                ## Career 分析总结

                **岗位**: %s | **公司**: %s | **匹配度**: %d%% (%s)

                **分析完成度**: %d/%d 任务成功

                %s

                **下一步建议**:
                - 查看简历优化建议，按 STAR 原则更新简历
                - 参考学习计划补齐技能差距
                - 使用面试准备方案进行模拟面试练习
                """,
                report.getJobTitle() != null ? report.getJobTitle() : "目标岗位",
                report.getCompany() != null ? report.getCompany() : "未知",
                score, level,
                successCount, tasks.size(),
                Boolean.FALSE.equals(report.getProfileSufficient())
                        ? "⚠️ **资料不足提示**: 个人知识库资料较少，建议补充简历、项目文档、实习证明等资料以获得更精准的分析。"
                        : "✅ 个人知识库资料已用于本次分析。");
    }

    // ===== JSON 辅助方法 =====

    private Map<String, Object> parseJsonToMap(String json) {
        try {
            String clean = json.trim();
            if (clean.startsWith("```")) {
                int s = clean.indexOf("{");
                int e = clean.lastIndexOf("}");
                if (s >= 0 && e > s) clean = clean.substring(s, e + 1);
            }
            return objectMapper.readValue(clean, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse JSON: {}", json.substring(0, Math.min(100, json.length())));
            return Map.of();
        }
    }

    private Integer extractInt(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number n) return n.intValue();
        try { return Integer.parseInt(obj.toString()); } catch (Exception e) { return null; }
    }

    private String extractString(Object obj) {
        return obj != null ? obj.toString() : null;
    }

    private String toJsonString(Object obj) {
        if (obj == null) return null;
        try { return objectMapper.writeValueAsString(obj); } catch (Exception e) { return obj.toString(); }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    /**
     * 异步执行完整 Workflow（在 workflowExecutor 线程池中运行）
     * 包含：任务规划 → Context 初始化 → 任务执行 → 结果汇总
     * <p>
     * 注意：此方法为非 public，仅供 CompletableFuture.runAsync 调用，不使用 @Async 注解
     * （@Async 在同类内部自调用时不生效，故改用 CompletableFuture + 显式 Executor）
     */
    private void executeWorkflowAsyncInternal(Long userId, String workflowId, String userGoal) {
        long workflowStart = System.currentTimeMillis();
        log.info("[Async] Workflow {} started in thread {}", workflowId, Thread.currentThread().getName());

        try {
            // 1. 任务规划（LLM 调用，~3-5秒）
            eventBus.publish(workflowId, WorkflowEvent.builder()
                    .event("task_started")
                    .workflowId(workflowId)
                    .agentType("master")
                    .task("任务规划（LLM 拆解用户目标）")
                    .taskType("PLANNING")
                    .status("RUNNING")
                    .message("MasterAgent 正在拆解目标为多个子任务...")
                    .progress(3)
                    .timestamp(now())
                    .build());

            List<AgentTask> tasks = planTasks(userId, workflowId, userGoal);

            if (tasks.isEmpty()) {
                eventBus.publish(workflowId, WorkflowEvent.builder()
                        .event("workflow_failed")
                        .workflowId(workflowId)
                        .status("FAILED")
                        .message("任务规划失败，无法拆解目标")
                        .errorMessage("planTasks returned empty list")
                        .timestamp(now())
                        .build());
                eventBus.complete(workflowId);
                return;
            }

            eventBus.publish(workflowId, WorkflowEvent.builder()
                    .event("task_completed")
                    .workflowId(workflowId)
                    .agentType("master")
                    .taskType("PLANNING")
                    .status("SUCCESS")
                    .message(String.format("规划完成，共 %d 个子任务", tasks.size()))
                    .totalTasks(tasks.size())
                    .progress(5)
                    .timestamp(now())
                    .build());

            // 2. 初始化 WorkflowContext（Personal RAG 一次性检索）
            eventBus.publish(workflowId, WorkflowEvent.builder()
                    .event("task_started")
                    .workflowId(workflowId)
                    .agentType("rag")
                    .task("初始化用户画像（Personal RAG 检索）")
                    .taskType("CONTEXT_INIT")
                    .status("RUNNING")
                    .message("正在检索用户个人知识库...")
                    .progress(8)
                    .timestamp(now())
                    .build());

            WorkflowContext context = initializeContext(userId, workflowId, userGoal);
            log.info("[Async] Workflow {} context initialized: ragSuccess={}, profileLength={}",
                    workflowId, context.isRagRetrievalSuccess(),
                    context.getUserProfile() != null ? context.getUserProfile().length() : 0);

            eventBus.publish(workflowId, WorkflowEvent.builder()
                    .event("task_completed")
                    .workflowId(workflowId)
                    .agentType("rag")
                    .taskType("CONTEXT_INIT")
                    .status(context.isRagRetrievalSuccess() ? "SUCCESS" : "DEGRADED")
                    .message(context.isRagRetrievalSuccess()
                            ? "用户画像检索完成（" + context.getUserProfile().length() + " 字符）"
                            : "RAG 检索失败，降级为通用上下文: " + context.getRagRetrievalError())
                    .progress(10)
                    .timestamp(now())
                    .build());

            // 3. Sprint 6-C: DAG 并行执行任务（无依赖任务并行，有依赖任务等待前置完成）
            executeTasksDAG(userId, workflowId, tasks, context);

            // 4. 结果汇总
            eventBus.publish(workflowId, WorkflowEvent.builder()
                    .event("task_started")
                    .workflowId(workflowId)
                    .agentType("master")
                    .taskType("SUMMARY")
                    .task("MasterAgent 综合汇总")
                    .status("RUNNING")
                    .message("正在汇总各 Agent 结果生成最终建议...")
                    .progress(95)
                    .timestamp(now())
                    .build());

            String summary = summarizeWorkflow(userId, userGoal, tasks);

            long totalDuration = System.currentTimeMillis() - workflowStart;
            long successCount = tasks.stream().filter(t -> "SUCCESS".equals(t.getStatus())).count();
            long failedCount = tasks.stream().filter(t -> "FAILED".equals(t.getStatus())).count();
            String workflowStatus = failedCount == 0 ? "SUCCESS" : (successCount > 0 ? "PARTIAL" : "FAILED");

            // 5. 发布 workflow_completed
            eventBus.publish(workflowId, WorkflowEvent.builder()
                    .event("workflow_completed")
                    .workflowId(workflowId)
                    .status(workflowStatus)
                    .message(String.format("工作流完成: %d/%d 成功, 耗时 %dms",
                            successCount, tasks.size(), totalDuration))
                    .summary(summary)
                    .totalTasks(tasks.size())
                    .completedTasks((int) successCount)
                    .progress(100)
                    .durationMs(totalDuration)
                    .timestamp(now())
                    .build());

            log.info("[Async] Workflow {} done: {}/{} success, {}ms",
                    workflowId, successCount, tasks.size(), totalDuration);

        } catch (Exception e) {
            log.error("[Async] Workflow {} failed unexpectedly", workflowId, e);
            eventBus.publish(workflowId, WorkflowEvent.builder()
                    .event("workflow_failed")
                    .workflowId(workflowId)
                    .status("FAILED")
                    .message("工作流异常: " + e.getMessage())
                    .errorMessage(e.getMessage())
                    .timestamp(now())
                    .build());
        } finally {
            eventBus.complete(workflowId);
        }
    }

    // ============================================================
    // Sprint 6-B: WorkflowContext 初始化（Task 2 + Task 3）
    // ============================================================

    /**
     * Sprint 6-C: 初始化 WorkflowContext
     * <p>
     * 使用 PersonalProfileService 检索带来源信息的结构化用户画像：
     * - ProfileQueryBuilder 生成短而精准的 query（避免 embedding 完整用户目标）
     * - 多 query 检索提高召回率
     * - 返回带来源标注的 skills/projects/experiences
     * <p>
     * 失败时降级为空 Context，不阻塞 Workflow
     */
    private WorkflowContext initializeContext(Long userId, String workflowId, String userGoal) {
        long start = System.currentTimeMillis();
        try {
            // Sprint 6-C: 使用 PersonalProfileService 检索带来源的结构化画像
            UserProfileContext profileContext = personalProfileService.retrieveProfile(userId, userGoal);

            WorkflowContext context = WorkflowContext.builder()
                    .userId(userId)
                    .workflowId(workflowId)
                    .userGoal(userGoal)
                    .userProfile(profileContext.getProfileText())
                    .skills(profileContext.getSkills() != null
                            ? profileContext.getSkills().stream().map(UserProfileContext.SourcedSkill::getValue).toList()
                            : List.of())
                    .projects(List.of())  // 已在 userProfileContext 中结构化
                    .experiences(List.of())
                    .ragRetrievalSuccess(profileContext.isRetrievalSuccess())
                    .ragRetrievalError(profileContext.getRetrievalError())
                    .userProfileContext(profileContext)
                    .createdAtMillis(start)
                    .build();

            log.info("WorkflowContext initialized (Sprint 6-C): userId={}, ragSuccess={}, profileLength={}, sourceDocs={}, elapsed={}ms",
                    userId, profileContext.isRetrievalSuccess(),
                    profileContext.getProfileText() != null ? profileContext.getProfileText().length() : 0,
                    profileContext.getSourceDocuments() != null ? profileContext.getSourceDocuments().size() : 0,
                    System.currentTimeMillis() - start);
            return context;
        } catch (Exception e) {
            log.warn("WorkflowContext initialization failed, degrading: {}", e.getMessage());
            return WorkflowContext.degraded(userId, workflowId, userGoal, e.getMessage());
        }
    }

    // ============================================================
    // Sprint 6-C: DAG 并行任务执行
    // ============================================================

    /**
     * DAG 并行执行任务
     * <p>
     * 规则：
     * - 无 dependsOn 的任务：立即并行执行
     * - 有 dependsOn 的任务：等待前置任务 SUCCESS 后再执行
     * - 前置任务 FAILED：当前任务标记为 FAILED（跳过执行）
     * <p>
     * 实现：使用 CompletableFuture + workflowExecutor 线程池
     */
    private void executeTasksDAG(Long userId, String workflowId, List<AgentTask> tasks, WorkflowContext context) {
        int total = tasks.size();
        Map<Long, CompletableFuture<Void>> taskFutures = new HashMap<>();
        Map<Long, AgentTask> taskMap = new HashMap<>();
        for (AgentTask t : tasks) {
            taskMap.put(t.getId(), t);
        }

        for (AgentTask task : tasks) {
            CompletableFuture<Void> future;

            if (task.getDependsOn() == null) {
                // 无依赖：直接并行执行
                int progressBase = 10 + (int) ((80.0 * tasks.indexOf(task)) / total);
                future = CompletableFuture.runAsync(
                        () -> executeAndPublishTask(userId, workflowId, task, tasks, context, progressBase),
                        workflowExecutor
                );
            } else {
                // 有依赖：等待前置任务完成后执行
                CompletableFuture<Void> depFuture = taskFutures.get(task.getDependsOn());
                if (depFuture == null) {
                    // 前置任务不存在，直接失败
                    int progressBase = 10 + (int) ((80.0 * tasks.indexOf(task)) / total);
                    future = CompletableFuture.runAsync(
                            () -> {
                                task.setStatus("FAILED");
                                task.setErrorMessage("前置任务不存在: " + task.getDependsOn());
                                task.setCompletedAt(LocalDateTime.now());
                                taskRepository.save(task);
                                publishTaskFailed(workflowId, task, "前置任务不存在", progressBase);
                            },
                            workflowExecutor
                    );
                } else {
                    int progressBase = 10 + (int) ((80.0 * tasks.indexOf(task)) / total);
                    final Long depTaskId = task.getDependsOn();
                    future = depFuture.thenRunAsync(() -> {
                        // 检查前置任务状态
                        AgentTask dep = taskRepository.findById(depTaskId).orElse(null);
                        if (dep == null || !"SUCCESS".equals(dep.getStatus())) {
                            task.setStatus("FAILED");
                            task.setErrorMessage("前置任务未成功完成: " + (dep == null ? "不存在" : dep.getStatus()));
                            task.setCompletedAt(LocalDateTime.now());
                            taskRepository.save(task);
                            publishTaskFailed(workflowId, task, "前置任务未成功完成", progressBase);
                        } else {
                            executeAndPublishTask(userId, workflowId, task, tasks, context, progressBase);
                        }
                    }, workflowExecutor);
                }
            }
            taskFutures.put(task.getId(), future);
        }

        // 等待所有任务完成
        try {
            CompletableFuture.allOf(taskFutures.values().toArray(new CompletableFuture[0]))
                    .get(10, java.util.concurrent.TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("DAG execution timed out or failed for workflow {}", workflowId, e);
        }
    }

    /**
     * 执行单个任务并发布 SSE 事件
     */
    private void executeAndPublishTask(Long userId, String workflowId, AgentTask task,
                                        List<AgentTask> allTasks, WorkflowContext context, int progressBase) {
        // Sprint 7-C-B: 设置 LLM 调用上下文（异步线程内部设置，ThreadLocal 生效）
        LLMCallContext.set(userId, workflowId, task.getAgentType());
        try {
            publishTaskStarted(workflowId, task, progressBase);
            executeSingleTaskWithContext(userId, task, allTasks, context);
            if ("SUCCESS".equals(task.getStatus())) {
                publishTaskCompleted(workflowId, task, progressBase + 10);
            } else {
                publishTaskFailed(workflowId, task, task.getErrorMessage(), progressBase + 10);
            }
        } finally {
            LLMCallContext.clear();
        }
    }

    // ============================================================
    // 任务执行（注入 WorkflowContext）
    // ============================================================

    /**
     * 执行单个任务，将 WorkflowContext + 前置任务输出作为上下文
     */
    private void executeSingleTaskWithContext(Long userId, AgentTask task, List<AgentTask> allTasks,
                                              WorkflowContext context) {
        long start = System.currentTimeMillis();
        task.setStatus("RUNNING");
        task.setStartedAt(LocalDateTime.now());
        taskRepository.save(task);
        log.info("Executing task id={}, type={}, agent={}", task.getId(), task.getTaskType(), task.getAgentType());

        try {
            // 构造输入：任务目标 + 输入参数 + 前置任务输出 + WorkflowContext
            String input = buildTaskInputWithContext(task, allTasks, context);

            FocusAgent agent = agentRegistry.getAgent(task.getAgentType()).orElse(null);
            if (agent == null) {
                throw new IllegalStateException("Agent not found: " + task.getAgentType());
            }

            // 将 WorkflowContext 渲染为文本作为 Agent 的 context 参数
            String agentContext = "工作流任务类型: " + task.getTaskType() + "\n"
                    + "输入参数: " + (task.getInputParams() != null ? task.getInputParams() : "无") + "\n\n"
                    + context.renderAsPromptContext();

            String result = agent.handle(input, userId, agentContext);

            task.setResult(result);
            task.setStatus("SUCCESS");
            task.setCompletedAt(LocalDateTime.now());
            task.setDurationMs(System.currentTimeMillis() - start);
            taskRepository.save(task);
            log.info("Task {} SUCCESS, durationMs={}", task.getId(), task.getDurationMs());
        } catch (Exception e) {
            task.setStatus("FAILED");
            task.setErrorMessage(e.getMessage());
            task.setCompletedAt(LocalDateTime.now());
            task.setDurationMs(System.currentTimeMillis() - start);
            taskRepository.save(task);
            log.error("Task {} FAILED, durationMs={}", task.getId(), task.getDurationMs(), e);
        }
    }

    /**
     * 构造任务输入：融合用户目标、输入参数、前置任务输出、WorkflowContext
     */
    private String buildTaskInputWithContext(AgentTask task, List<AgentTask> allTasks, WorkflowContext context) {
        StringBuilder input = new StringBuilder();
        input.append("任务目标: ").append(task.getGoal()).append("\n");
        if (task.getInputParams() != null) {
            input.append("输入参数: ").append(task.getInputParams()).append("\n");
        }

        // 追加前置任务的输出结果（链式调用核心）
        if (task.getDependsOn() != null) {
            allTasks.stream()
                    .filter(t -> t.getId().equals(task.getDependsOn()))
                    .findFirst()
                    .ifPresent(dep -> {
                        input.append("\n【前置任务输出】\n");
                        input.append("前置任务类型: ").append(dep.getTaskType()).append("\n");
                        input.append("前置任务结果:\n").append(dep.getResult()).append("\n");
                    });
        }

        return input.toString();
    }

    // ============================================================
    // SSE 事件发布辅助方法
    // ============================================================

    private void publishTaskStarted(String workflowId, AgentTask task, int progress) {
        eventBus.publish(workflowId, WorkflowEvent.builder()
                .event("task_started")
                .workflowId(workflowId)
                .taskId(task.getId())
                .taskType(task.getTaskType())
                .agentType(task.getAgentType())
                .task(task.getGoal())
                .status("RUNNING")
                .progress(progress)
                .message("开始执行: " + task.getGoal())
                .timestamp(now())
                .build());
    }

    private void publishTaskCompleted(String workflowId, AgentTask task, int progress) {
        eventBus.publish(workflowId, WorkflowEvent.builder()
                .event("task_completed")
                .workflowId(workflowId)
                .taskId(task.getId())
                .taskType(task.getTaskType())
                .agentType(task.getAgentType())
                .task(task.getGoal())
                .status("SUCCESS")
                .progress(progress)
                .durationMs(task.getDurationMs())
                .message("完成: " + task.getGoal() + " (" + task.getDurationMs() + "ms)")
                .timestamp(now())
                .build());
    }

    private void publishTaskFailed(String workflowId, AgentTask task, String error, int progress) {
        eventBus.publish(workflowId, WorkflowEvent.builder()
                .event("task_completed")
                .workflowId(workflowId)
                .taskId(task.getId())
                .taskType(task.getTaskType())
                .agentType(task.getAgentType())
                .task(task.getGoal())
                .status("FAILED")
                .progress(progress)
                .durationMs(task.getDurationMs())
                .errorMessage(error)
                .message("失败: " + task.getGoal() + " - " + error)
                .timestamp(now())
                .build());
    }

    private WorkflowEvent buildEvent(String event, String workflowId, String message,
                                     int completedTasks, int totalTasks) {
        return WorkflowEvent.builder()
                .event(event)
                .workflowId(workflowId)
                .message(message)
                .completedTasks(completedTasks)
                .totalTasks(totalTasks)
                .timestamp(now())
                .build();
    }

    private static String now() {
        return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    // ============================================================
    // 任务规划（保持原逻辑）
    // ============================================================

    /**
     * 任务规划：调用 LLM 将用户目标拆解为多个 AgentTask
     */
    private List<AgentTask> planTasks(Long userId, String workflowId, String userGoal) {
        String prompt = String.format("""
                你是 FocusOS AI 的任务规划器。请将用户目标拆解为多个子任务，每个子任务由对应 Agent 执行。

                用户目标：%s

                可用 Agent：
                - career: 职业规划、JD分析、岗位匹配、简历优化
                - learning: 学习计划生成、学习方法指导、每日学习任务
                - rag: 个人知识库检索、能力差距分析、资料引用

                任务类型（taskType）：
                - CAREER_ANALYSIS: 分析目标岗位要求
                - LEARNING_PLAN: 生成学习路线
                - SKILL_GAP: 分析当前能力差距
                - DAILY_TASK: 生成每日任务
                - RESUME_OPTIMIZE: 简历优化建议

                严格按以下 JSON 数组格式输出（不要输出其他内容）：
                [
                  {
                    "goal": "任务目标描述",
                    "taskType": "CAREER_ANALYSIS",
                    "agentType": "career",
                    "dependsOn": null,
                    "inputParams": "传递给 Agent 的输入参数（如目标岗位、时间等）"
                  },
                  {
                    "goal": "任务目标描述",
                    "taskType": "LEARNING_PLAN",
                    "agentType": "learning",
                    "dependsOn": 0,
                    "inputParams": "基于前置任务输出生成学习计划"
                  }
                ]

                规则：
                - dependsOn 为 null 表示无依赖，数字表示依赖数组中第几个任务（从0开始）
                - 任务数量 2-5 个，按执行顺序排列
                - 必须结合用户目标，不能编造无关任务
                - inputParams 简洁明了，传递关键信息
                """, userGoal);

        try {
            String plannerSystemPrompt = """
                    你是 FocusOS AI 平台的 Multi-Agent 任务规划器。
                    输出要求：
                    1. 严格输出 JSON 数组，不要输出任何其他内容
                    2. 不要使用 markdown 代码块包裹
                    3. 任务必须可执行、具体、与用户目标相关
                    """;
            String fullPrompt = plannerSystemPrompt + "\n\n" + prompt;
            String response = chatLanguageModel.chat(fullPrompt).trim();
            log.debug("Planner raw response: {}", response);

            String json = extractJsonArray(response);
            List<Map<String, Object>> taskPlans = objectMapper.readValue(json, new TypeReference<>() {});

            List<AgentTask> tasks = new ArrayList<>();
            Map<Integer, Long> indexToId = new HashMap<>();

            for (int i = 0; i < taskPlans.size(); i++) {
                Map<String, Object> plan = taskPlans.get(i);
                AgentTask task = new AgentTask();
                task.setUserId(userId);
                task.setWorkflowId(workflowId);
                task.setGoal((String) plan.get("goal"));
                task.setTaskType((String) plan.get("taskType"));
                task.setAgentType((String) plan.get("agentType"));
                task.setInputParams(plan.get("inputParams") != null ? plan.get("inputParams").toString() : null);
                task.setStatus("PLANNING");

                Object dep = plan.get("dependsOn");
                if (dep != null && !"null".equals(dep.toString())) {
                    try {
                        int depIndex = Integer.parseInt(dep.toString());
                        task.setDependsOn(indexToId.get(depIndex));
                    } catch (NumberFormatException ignore) {
                    }
                }

                AgentTask saved = taskRepository.save(task);
                indexToId.put(i, saved.getId());
                tasks.add(saved);
                log.info("Planned task #{}: type={}, agent={}, goal={}", i + 1, task.getTaskType(), task.getAgentType(), task.getGoal());
            }

            return tasks;
        } catch (Exception e) {
            log.error("Task planning failed for goal: {}", userGoal, e);
            return Collections.emptyList();
        }
    }

    /**
     * 汇总工作流结果
     */
    private String summarizeWorkflow(Long userId, String userGoal, List<AgentTask> tasks) {
        StringBuilder taskResults = new StringBuilder();
        for (AgentTask task : tasks) {
            taskResults.append("## 任务: ").append(task.getGoal()).append("\n");
            taskResults.append("- 类型: ").append(task.getTaskType())
                    .append(" | Agent: ").append(task.getAgentType())
                    .append(" | 耗时: ").append(task.getDurationMs() != null ? task.getDurationMs() + "ms" : "未知").append("\n");
            taskResults.append("- 状态: ").append(task.getStatus()).append("\n");
            if ("SUCCESS".equals(task.getStatus()) && task.getResult() != null) {
                String preview = task.getResult().length() > 1500 ? task.getResult().substring(0, 1500) + "..." : task.getResult();
                taskResults.append("- 结果:\n").append(preview).append("\n\n");
            } else if (task.getErrorMessage() != null) {
                taskResults.append("- 错误: ").append(task.getErrorMessage()).append("\n\n");
            }
        }

        String prompt = String.format("""
                你是 FocusOS AI 的 MasterAgent。请基于以下多 Agent 协作结果，为用户生成一份综合总结。

                用户目标：%s

                各 Agent 执行结果：
                %s

                请生成结构化总结，包含：
                1. 整体分析（目标可行性评估）
                2. 核心发现（来自各 Agent 的关键洞察）
                3. 行动建议（按优先级排列，具体可执行）
                4. 时间规划（短期1周 / 中期1月 / 长期3月）

                请用中文回答，专业、具体、有指导性。
                """, userGoal, taskResults);

        try {
            return chatLanguageModel.chat(prompt);
        } catch (Exception e) {
            log.error("Workflow summary failed", e);
            return "工作流汇总生成失败，请查看各任务结果。";
        }
    }

    /**
     * 从 LLM 响应中提取 JSON 数组
     */
    private String extractJsonArray(String response) {
        String trimmed = response.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf("[");
            int end = trimmed.lastIndexOf("]");
            if (start >= 0 && end > start) {
                return trimmed.substring(start, end + 1);
            }
        }
        int start = trimmed.indexOf("[");
        int end = trimmed.lastIndexOf("]");
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    // ============================================================
    // 历史查询（保持原逻辑，供页面刷新恢复）
    // ============================================================

    /**
     * 获取用户的工作流历史
     */
    public List<WorkflowResponse> getUserWorkflows(Long userId) {
        List<AgentTask> allTasks = taskRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Map<String, List<AgentTask>> grouped = new LinkedHashMap<>();
        for (AgentTask task : allTasks) {
            grouped.computeIfAbsent(task.getWorkflowId(), k -> new ArrayList<>()).add(task);
        }

        List<WorkflowResponse> workflows = new ArrayList<>();
        for (Map.Entry<String, List<AgentTask>> entry : grouped.entrySet()) {
            List<AgentTask> tasks = entry.getValue();
            long success = tasks.stream().filter(t -> "SUCCESS".equals(t.getStatus())).count();
            long failed = tasks.stream().filter(t -> "FAILED".equals(t.getStatus())).count();

            // 判断是否仍在运行（有 PLANNING/RUNNING 状态任务）
            boolean running = tasks.stream().anyMatch(t -> "PLANNING".equals(t.getStatus()) || "RUNNING".equals(t.getStatus()));
            String status = running ? "RUNNING" : (failed == 0 ? "SUCCESS" : (success > 0 ? "PARTIAL" : "FAILED"));

            workflows.add(WorkflowResponse.builder()
                    .workflowId(entry.getKey())
                    .userGoal(tasks.get(0).getGoal())
                    .status(status)
                    .totalTasks(tasks.size())
                    .successTasks((int) success)
                    .failedTasks((int) failed)
                    .createdAt(tasks.get(0).getCreatedAt())
                    .tasks(tasks.stream().map(com.focusos.dto.response.AgentTaskResponse::fromEntity).toList())
                    .build());
        }
        return workflows;
    }

    /**
     * 获取指定 workflowId 的详情（页面刷新恢复用）
     */
    public WorkflowResponse getWorkflow(String workflowId) {
        List<AgentTask> tasks = taskRepository.findByWorkflowIdOrderByCreatedAtAsc(workflowId);
        if (tasks.isEmpty()) {
            return null;
        }
        long success = tasks.stream().filter(t -> "SUCCESS".equals(t.getStatus())).count();
        long failed = tasks.stream().filter(t -> "FAILED".equals(t.getStatus())).count();
        boolean running = tasks.stream().anyMatch(t -> "PLANNING".equals(t.getStatus()) || "RUNNING".equals(t.getStatus()));
        String status = running ? "RUNNING" : (failed == 0 ? "SUCCESS" : (success > 0 ? "PARTIAL" : "FAILED"));

        return WorkflowResponse.builder()
                .workflowId(workflowId)
                .userGoal(tasks.get(0).getGoal())
                .status(status)
                .totalTasks(tasks.size())
                .successTasks((int) success)
                .failedTasks((int) failed)
                .createdAt(tasks.get(0).getCreatedAt())
                .tasks(tasks.stream().map(com.focusos.dto.response.AgentTaskResponse::fromEntity).toList())
                .build();
    }
}
