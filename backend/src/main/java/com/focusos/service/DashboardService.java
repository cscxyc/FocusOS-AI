package com.focusos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.focusos.agent.MasterAgent;
import com.focusos.dto.response.DashboardOverviewResponse;
import com.focusos.dto.response.DashboardOverviewResponse.*;
import com.focusos.dto.response.DashboardResponse;
import com.focusos.dto.response.TimelineResponse;
import com.focusos.dto.response.TimelineResponse.TimelineStage;
import com.focusos.dto.response.WorkflowResponse;
import com.focusos.entity.*;
import com.focusos.exception.ResourceNotFoundException;
import com.focusos.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final UserRepository userRepository;
    private final LearningPlanRepository learningPlanRepository;
    private final LearningSessionRepository learningSessionRepository;
    private final ScheduleEventRepository scheduleEventRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final CareerProfileRepository careerProfileRepository;
    private final AgentTaskRepository agentTaskRepository;
    private final MasterAgent masterAgent;

    // Sprint 9-A: 新增 Repository（Dashboard 聚合数据源）
    private final ResumeVersionRepository resumeVersionRepository;
    private final CareerAnalysisReportRepository careerAnalysisReportRepository;
    private final InterviewSessionRepository interviewSessionRepository;
    private final CareerGrowthPlanRepository careerGrowthPlanRepository;
    private final UserMemoryRepository userMemoryRepository;
    private final WorkflowInstanceRepository workflowInstanceRepository;
    private final LLMCallLogRepository llmCallLogRepository;
    private final ObjectMapper objectMapper;

    public DashboardResponse getDashboardData(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("用户", userId));

        Map<String, Object> learningStats = buildLearningStats(userId);
        List<Map<String, Object>> recentSessions = buildRecentSessions(userId);
        List<Map<String, Object>> todayEvents = buildTodayEvents(userId);
        Map<String, Object> careerStats = buildCareerStats(userId);

        DashboardResponse response = new DashboardResponse();
        response.setUserId(userId);
        response.setUsername(user.getUsername());
        response.setLearningStats(learningStats);
        response.setRecentSessions(recentSessions);
        response.setTodayEvents(todayEvents);
        response.setCareerStats(careerStats);

        return response;
    }

    // ============================================================
    // Sprint 9-A: Dashboard Overview（聚合用户 AI 职业状态）
    // ============================================================

    /**
     * Sprint 9-A: 获取 Dashboard 首页聚合数据。
     * <p>
     * 聚合 5 个核心维度：简历评分、JD 匹配最高分、模拟面试成绩、学习成长进度、Memory 数量。
     * 所有数据通过 Repository 直接查询，不调用其他 Controller 或 Service。
     */
    public DashboardOverviewResponse getOverview(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("用户", userId));

        return DashboardOverviewResponse.builder()
                .userId(userId)
                .username(user.getUsername())
                .resumeScore(buildResumeSummary(userId))
                .highestMatchScore(buildCareerSummary(userId))
                .interviewScore(buildInterviewSummary(userId))
                .growthProgress(buildGrowthSummary(userId))
                .memoryCount(countActiveMemories(userId))
                .recentActivities(buildRecentActivities(userId))
                .generatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 简历评分摘要：取当前激活版本，通过 sourceReportId 关联 CareerAnalysisReport.matchScore。
     */
    private ResumeSummary buildResumeSummary(Long userId) {
        List<ResumeVersion> versions = resumeVersionRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Optional<ResumeVersion> activeOpt = versions.stream().filter(ResumeVersion::getIsActive).findFirst();
        // 若无激活版本但有任意版本，取最新版本展示
        ResumeVersion target = activeOpt.orElse(versions.isEmpty() ? null : versions.get(0));

        Integer score = null;
        if (target != null && target.getSourceReportId() != null) {
            score = careerAnalysisReportRepository.findById(target.getSourceReportId())
                    .map(CareerAnalysisReport::getMatchScore)
                    .orElse(null);
        }

        return ResumeSummary.builder()
                .resumeId(target != null ? target.getId() : null)
                .versionName(target != null ? target.getVersionName() : null)
                .targetPosition(target != null ? target.getTargetPosition() : null)
                .score(score)
                .totalVersions(versions.size())
                .hasActiveVersion(activeOpt.isPresent())
                .build();
    }

    /**
     * JD 匹配最高分摘要：从所有 CareerAnalysisReport 中取 matchScore 最高的。
     */
    private CareerSummary buildCareerSummary(Long userId) {
        List<CareerAnalysisReport> reports = careerAnalysisReportRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Optional<CareerAnalysisReport> best = reports.stream()
                .filter(r -> r.getMatchScore() != null)
                .max(Comparator.comparingInt(CareerAnalysisReport::getMatchScore));

        return CareerSummary.builder()
                .matchScore(best.map(CareerAnalysisReport::getMatchScore).orElse(null))
                .jobTitle(best.map(CareerAnalysisReport::getJobTitle).orElse(null))
                .company(best.map(CareerAnalysisReport::getCompany).orElse(null))
                .workflowId(best.map(CareerAnalysisReport::getWorkflowId).orElse(null))
                .totalReports(reports.size())
                .latestAnalysisAt(reports.isEmpty() ? null : reports.get(0).getCreatedAt())
                .build();
    }

    /**
     * 模拟面试成绩摘要：统计所有已完成（COMPLETED）且有 score 的面试会话。
     */
    private InterviewSummary buildInterviewSummary(Long userId) {
        List<InterviewSession> sessions = interviewSessionRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<InterviewSession> completedWithScore = sessions.stream()
                .filter(s -> "COMPLETED".equals(s.getStatus()) && s.getScore() != null)
                .collect(Collectors.toList());

        double avg = completedWithScore.stream().mapToInt(InterviewSession::getScore).average().orElse(0);
        int highest = completedWithScore.stream().mapToInt(InterviewSession::getScore).max().orElse(0);
        Integer latest = completedWithScore.isEmpty() ? null : completedWithScore.get(0).getScore();

        return InterviewSummary.builder()
                .averageScore(completedWithScore.isEmpty() ? null : (int) Math.round(avg))
                .highestScore(completedWithScore.isEmpty() ? null : highest)
                .latestScore(latest)
                .totalSessions(sessions.size())
                .completedSessions(completedWithScore.size())
                .latestJobTitle(completedWithScore.isEmpty() ? null : completedWithScore.get(0).getJobTitle())
                .build();
    }

    /**
     * 学习成长进度摘要：解析 growthPlanJson 提取总周数，按日期推算已完成周数。
     */
    private GrowthSummary buildGrowthSummary(Long userId) {
        List<CareerGrowthPlan> plans = careerGrowthPlanRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<CareerGrowthPlan> activePlans = plans.stream().filter(p -> "ACTIVE".equals(p.getStatus())).collect(Collectors.toList());
        CareerGrowthPlan latest = plans.isEmpty() ? null : plans.get(0);

        int totalWeeks = 12; // 默认 12 周
        int completedWeeks = 0;
        if (latest != null) {
            totalWeeks = extractTotalWeeksFromJson(latest.getGrowthPlanJson());
            // 按计划创建日期推算已完成周数（上限为 totalWeeks）
            long weeksElapsed = ChronoUnit.WEEKS.between(latest.getCreatedAt().toLocalDate(), LocalDate.now());
            completedWeeks = (int) Math.min(Math.max(weeksElapsed, 0), totalWeeks);
        }

        int progressPercent = totalWeeks > 0 ? (int) (100.0 * completedWeeks / totalWeeks) : 0;

        return GrowthSummary.builder()
                .totalWeeks(totalWeeks)
                .completedWeeks(completedWeeks)
                .progressPercent(progressPercent)
                .targetPosition(latest != null ? latest.getTargetPosition() : null)
                .currentLevel(latest != null ? latest.getCurrentLevel() : null)
                .activePlans(activePlans.size())
                .totalPlans(plans.size())
                .build();
    }

    /** 从 growthPlanJson 中解析总周数（兼容 roadmap.weeks / totalWeeks / weeks 等字段） */
    @SuppressWarnings("unchecked")
    private int extractTotalWeeksFromJson(String json) {
        if (json == null || json.isBlank()) return 12;
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, Map.class);
            // 尝试多种可能的字段路径
            if (parsed.containsKey("totalWeeks")) return toInt(parsed.get("totalWeeks"), 12);
            if (parsed.containsKey("weeks")) return toInt(parsed.get("weeks"), 12);
            Object roadmap = parsed.get("roadmap");
            if (roadmap instanceof Map) {
                Map<String, Object> rm = (Map<String, Object>) roadmap;
                if (rm.containsKey("totalWeeks")) return toInt(rm.get("totalWeeks"), 12);
                if (rm.containsKey("weeks")) return toInt(rm.get("weeks"), 12);
            }
            // weeklyTasks 数组长度作为兜底
            Object weeklyTasks = parsed.get("weeklyTasks");
            if (weeklyTasks instanceof List) {
                int size = ((List<?>) weeklyTasks).size();
                if (size > 0) return size;
            }
        } catch (Exception e) {
            log.debug("解析 growthPlanJson 失败，使用默认 12 周: {}", e.getMessage());
        }
        return 12;
    }

    private int toInt(Object obj, int defaultValue) {
        if (obj instanceof Number) return ((Number) obj).intValue();
        try { return Integer.parseInt(obj.toString()); } catch (Exception e) { return defaultValue; }
    }

    /** 统计用户活跃 Memory 数量 */
    private Integer countActiveMemories(Long userId) {
        return userMemoryRepository.findByUserIdOrderByUpdatedAtDesc(userId).size();
    }

    /**
     * 构建最近活动列表（跨实体聚合，按时间倒序，最多 10 条）。
     * 数据来源：CareerAnalysisReport / ResumeVersion / InterviewSession / CareerGrowthPlan / WorkflowInstance
     */
    private List<ActivityItem> buildRecentActivities(Long userId) {
        List<ActivityItem> activities = new ArrayList<>();

        // 1. CareerAnalysisReport
        careerAnalysisReportRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .limit(5).forEach(r -> activities.add(ActivityItem.builder()
                        .type("CAREER_ANALYSIS")
                        .title("JD 匹配分析")
                        .description(String.format("%s · 匹配度: %s",
                                r.getJobTitle() != null ? r.getJobTitle() : "未知岗位",
                                r.getMatchScore() != null ? r.getMatchScore() + "/100" : "待评估"))
                        .workflowId(r.getWorkflowId())
                        .status(r.getMatchScore() != null ? "SUCCESS" : "PENDING")
                        .createdAt(r.getCreatedAt())
                        .build()));

        // 2. ResumeVersion
        resumeVersionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .limit(5).forEach(r -> activities.add(ActivityItem.builder()
                        .type("RESUME_OPTIMIZATION")
                        .title("简历版本创建")
                        .description(String.format("%s · %s",
                                r.getVersionName() != null ? r.getVersionName() : "未命名版本",
                                r.getTargetPosition() != null ? r.getTargetPosition() : "未指定岗位"))
                        .status(r.getIsActive() != null && r.getIsActive() ? "ACTIVE" : "ARCHIVED")
                        .createdAt(r.getCreatedAt())
                        .build()));

        // 3. InterviewSession
        interviewSessionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .limit(5).forEach(s -> activities.add(ActivityItem.builder()
                        .type("INTERVIEW")
                        .title("模拟面试")
                        .description(String.format("%s · %s",
                                s.getJobTitle() != null ? s.getJobTitle() : "未知岗位",
                                s.getScore() != null ? "评分: " + s.getScore() : s.getStatus()))
                        .workflowId(s.getWorkflowId())
                        .status(s.getStatus())
                        .createdAt(s.getCreatedAt())
                        .build()));

        // 4. CareerGrowthPlan
        careerGrowthPlanRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .limit(5).forEach(p -> activities.add(ActivityItem.builder()
                        .type("GROWTH_PLAN")
                        .title("成长规划生成")
                        .description(String.format("%s · %s",
                                p.getTargetPosition() != null ? p.getTargetPosition() : "未指定岗位",
                                p.getCurrentLevel() != null ? p.getCurrentLevel() : "未定位"))
                        .status(p.getStatus())
                        .createdAt(p.getCreatedAt())
                        .build()));

        // 5. WorkflowInstance（最近工作流）
        workflowInstanceRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .limit(5).forEach(w -> activities.add(ActivityItem.builder()
                        .type("WORKFLOW")
                        .title("工作流执行")
                        .description(String.format("%s · 进度: %s%%",
                                w.getWorkflowType() != null ? w.getWorkflowType() : "UNKNOWN",
                                w.getProgress() != null ? w.getProgress() : 0))
                        .workflowId(w.getWorkflowId())
                        .status(w.getStatus())
                        .createdAt(w.getCreatedAt())
                        .build()));

        // 按时间倒序排序，取前 10 条
        return activities.stream()
                .sorted(Comparator.comparing(ActivityItem::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .collect(Collectors.toList());
    }

    // ============================================================
    // Sprint 9-A: Career Journey Timeline
    // ============================================================

    /**
     * Sprint 9-A: 获取 Career Journey Timeline。
     * <p>
     * 将用户求职流程可视化为 6 个有序阶段（对应 Career Workflow 的 6 步 DAG）：
     * CAREER_ANALYSIS → RESUME_OPTIMIZATION → SKILL_GAP_ANALYSIS
     * → LEARNING_PLAN → INTERVIEW_PREPARATION → MOCK_INTERVIEW
     * <p>
     * 每个阶段的状态来自关联的实体（WorkflowInstance / CareerAnalysisReport 等），
     * 无数据时状态为 SKIPPED。
     */
    public TimelineResponse getTimeline(Long userId) {
        // 6 个固定阶段定义
        String[] stages = {
                "CAREER_ANALYSIS", "RESUME_OPTIMIZATION", "SKILL_GAP_ANALYSIS",
                "LEARNING_PLAN", "INTERVIEW_PREPARATION", "MOCK_INTERVIEW"
        };
        String[] titles = {
                "JD 分析", "简历优化", "技能差距分析",
                "学习计划", "面试准备", "模拟面试"
        };

        // 查询数据
        List<CareerAnalysisReport> reports = careerAnalysisReportRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<ResumeVersion> resumes = resumeVersionRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<InterviewSession> sessions = interviewSessionRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<CareerGrowthPlan> plans = careerGrowthPlanRepository.findByUserIdOrderByCreatedAtDesc(userId);

        // 最新 report（作为 timeline 主线）
        CareerAnalysisReport latestReport = reports.isEmpty() ? null : reports.get(0);
        String mainWorkflowId = latestReport != null ? latestReport.getWorkflowId() : null;

        List<TimelineStage> stageList = new ArrayList<>();

        // Stage 1: CAREER_ANALYSIS
        if (latestReport != null) {
            stageList.add(TimelineStage.builder()
                    .stage(stages[0]).title(titles[0])
                    .status(latestReport.getMatchScore() != null ? "SUCCESS" : "PENDING")
                    .description(String.format("%s%s · 匹配度: %s",
                            latestReport.getJobTitle() != null ? latestReport.getJobTitle() : "未知岗位",
                            latestReport.getCompany() != null ? " @" + latestReport.getCompany() : "",
                            latestReport.getMatchScore() != null ? latestReport.getMatchScore() : "待评估"))
                    .workflowId(mainWorkflowId)
                    .entityId(latestReport.getId())
                    .createdAt(latestReport.getCreatedAt())
                    .build());
        } else {
            stageList.add(TimelineStage.builder()
                    .stage(stages[0]).title(titles[0])
                    .status("SKIPPED")
                    .description("尚未进行 JD 分析")
                    .build());
        }

        // Stage 2: RESUME_OPTIMIZATION
        ResumeVersion latestResume = resumes.isEmpty() ? null : resumes.get(0);
        if (latestResume != null) {
            stageList.add(TimelineStage.builder()
                    .stage(stages[1]).title(titles[1])
                    .status("SUCCESS")
                    .description(String.format("%s · %s",
                            latestResume.getVersionName() != null ? latestResume.getVersionName() : "简历版本",
                            latestResume.getTargetPosition() != null ? latestResume.getTargetPosition() : "未指定岗位"))
                    .entityId(latestResume.getId())
                    .createdAt(latestResume.getCreatedAt())
                    .build());
        } else {
            stageList.add(TimelineStage.builder()
                    .stage(stages[1]).title(titles[1])
                    .status("SKIPPED")
                    .description("尚未生成优化简历")
                    .build());
        }

        // Stage 3: SKILL_GAP_ANALYSIS（复用 latestReport 的 gaps 字段）
        if (latestReport != null && latestReport.getGaps() != null && !latestReport.getGaps().isBlank()) {
            stageList.add(TimelineStage.builder()
                    .stage(stages[2]).title(titles[2])
                    .status("SUCCESS")
                    .description("已识别技能差距（来自 JD 分析）")
                    .workflowId(mainWorkflowId)
                    .createdAt(latestReport.getCreatedAt())
                    .build());
        } else {
            stageList.add(TimelineStage.builder()
                    .stage(stages[2]).title(titles[2])
                    .status("SKIPPED")
                    .description("尚未进行技能差距分析")
                    .build());
        }

        // Stage 4: LEARNING_PLAN
        CareerGrowthPlan latestPlan = plans.isEmpty() ? null : plans.get(0);
        if (latestPlan != null) {
            int totalWeeks = extractTotalWeeksFromJson(latestPlan.getGrowthPlanJson());
            stageList.add(TimelineStage.builder()
                    .stage(stages[3]).title(titles[3])
                    .status("SUCCESS")
                    .description(String.format("%s · %d 周计划",
                            latestPlan.getTargetPosition() != null ? latestPlan.getTargetPosition() : "成长规划",
                            totalWeeks))
                    .entityId(latestPlan.getId())
                    .createdAt(latestPlan.getCreatedAt())
                    .build());
        } else if (latestReport != null && latestReport.getLearningPlan() != null) {
            stageList.add(TimelineStage.builder()
                    .stage(stages[3]).title(titles[3])
                    .status("SUCCESS")
                    .description("学习计划已生成（来自 JD 分析）")
                    .workflowId(mainWorkflowId)
                    .createdAt(latestReport.getCreatedAt())
                    .build());
        } else {
            stageList.add(TimelineStage.builder()
                    .stage(stages[3]).title(titles[3])
                    .status("SKIPPED")
                    .description("尚未生成学习计划")
                    .build());
        }

        // Stage 5: INTERVIEW_PREPARATION
        // 有 InterviewSession 即代表面试准备完成（Session 包含 questionsJson）
        InterviewSession latestSession = sessions.isEmpty() ? null : sessions.get(0);
        if (latestSession != null && latestSession.getQuestionsJson() != null) {
            stageList.add(TimelineStage.builder()
                    .stage(stages[4]).title(titles[4])
                    .status("SUCCESS")
                    .description(String.format("%s · 面试题已生成",
                            latestSession.getJobTitle() != null ? latestSession.getJobTitle() : "面试准备"))
                    .workflowId(latestSession.getWorkflowId())
                    .entityId(latestSession.getId())
                    .createdAt(latestSession.getCreatedAt())
                    .build());
        } else {
            stageList.add(TimelineStage.builder()
                    .stage(stages[4]).title(titles[4])
                    .status("SKIPPED")
                    .description("尚未生成面试题")
                    .build());
        }

        // Stage 6: MOCK_INTERVIEW
        if (latestSession != null && "COMPLETED".equals(latestSession.getStatus()) && latestSession.getScore() != null) {
            stageList.add(TimelineStage.builder()
                    .stage(stages[5]).title(titles[5])
                    .status("SUCCESS")
                    .description(String.format("%s · 评分: %d/100",
                            latestSession.getJobTitle() != null ? latestSession.getJobTitle() : "模拟面试",
                            latestSession.getScore()))
                    .workflowId(latestSession.getWorkflowId())
                    .entityId(latestSession.getId())
                    .createdAt(latestSession.getCreatedAt())
                    .build());
        } else if (latestSession != null && "IN_PROGRESS".equals(latestSession.getStatus())) {
            stageList.add(TimelineStage.builder()
                    .stage(stages[5]).title(titles[5])
                    .status("RUNNING")
                    .description(String.format("%s · 进行中（已回答 %d 题）",
                            latestSession.getJobTitle() != null ? latestSession.getJobTitle() : "模拟面试",
                            latestSession.getAnsweredCount() != null ? latestSession.getAnsweredCount() : 0))
                    .workflowId(latestSession.getWorkflowId())
                    .entityId(latestSession.getId())
                    .createdAt(latestSession.getCreatedAt())
                    .build());
        } else {
            stageList.add(TimelineStage.builder()
                    .stage(stages[5]).title(titles[5])
                    .status("SKIPPED")
                    .description("尚未完成模拟面试")
                    .build());
        }

        return TimelineResponse.builder()
                .workflowId(mainWorkflowId)
                .stages(stageList)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    public String getAIRecommendation(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("用户", userId));

        Map<String, Object> dashboardData = new HashMap<>();
        dashboardData.put("username", user.getUsername());
        dashboardData.put("todayMinutes", learningSessionRepository.sumDurationByUserIdAndDate(userId, LocalDate.now()));
        dashboardData.put("weeklyFocus", learningSessionRepository.avgFocusLevelByUserIdAndDateBetween(
                userId, LocalDate.now().minusWeeks(1), LocalDate.now()));
        dashboardData.put("activePlans", learningPlanRepository.findByUserIdAndStatus(userId, "ACTIVE").size());
        dashboardData.put("todayEvents", scheduleEventRepository.findByUserIdAndEventDate(userId, LocalDate.now()).size());

        return masterAgent.provideRecommendation(dashboardData);
    }

    /**
     * Sprint 6-A: Dashboard 接入 AI 工作流计划
     * <p>
     * 展示今日AI建议、本周目标、学习任务、职业进度
     */
    public Map<String, Object> getAIPlan(Long userId) {
        Map<String, Object> plan = new HashMap<>();

        // 1. 今日AI建议
        plan.put("dailyAdvice", getAIRecommendation(userId));

        // 2. 本周目标（从最近的工作流提取）
        List<WorkflowResponse> workflows = masterAgent.getUserWorkflows(userId);
        if (!workflows.isEmpty()) {
            WorkflowResponse latest = workflows.get(0);
            plan.put("latestWorkflow", latest);
            plan.put("weeklyGoal", latest.getUserGoal());
        }

        // 3. 学习任务（活跃的学习计划）
        List<LearningPlan> activePlans = learningPlanRepository.findByUserIdAndStatus(userId, "ACTIVE");
        List<Map<String, Object>> learningTasks = activePlans.stream().map(p -> {
            Map<String, Object> task = new HashMap<>();
            task.put("id", p.getId());
            task.put("title", p.getTitle());
            task.put("goal", p.getGoal());
            task.put("dailyTargetMinutes", p.getDailyTargetMinutes());
            task.put("endDate", p.getEndDate());
            return task;
        }).collect(Collectors.toList());
        plan.put("learningTasks", learningTasks);

        // 4. 职业进度（求职申请统计）
        List<JobApplication> applications = jobApplicationRepository.findByUserId(userId);
        Map<String, Object> careerProgress = new HashMap<>();
        careerProgress.put("totalApplications", applications.size());
        careerProgress.put("analyzed", applications.stream().filter(a -> "ANALYZED".equals(a.getStatus())).count());
        careerProgress.put("pending", applications.stream().filter(a -> "PENDING".equals(a.getStatus())).count());
        careerProgress.put("recentPositions", applications.stream()
                .sorted(Comparator.comparing(JobApplication::getId).reversed())
                .limit(3)
                .map(a -> {
                    Map<String, Object> pos = new HashMap<>();
                    pos.put("company", a.getCompany());
                    pos.put("position", a.getPosition());
                    pos.put("status", a.getStatus());
                    return pos;
                })
                .collect(Collectors.toList()));
        plan.put("careerProgress", careerProgress);

        // 5. AI工作流任务统计
        List<AgentTask> aiTasks = agentTaskRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Map<String, Object> aiTaskStats = new HashMap<>();
        aiTaskStats.put("totalWorkflows", aiTasks.stream().map(AgentTask::getWorkflowId).distinct().count());
        aiTaskStats.put("totalTasks", aiTasks.size());
        aiTaskStats.put("successTasks", aiTasks.stream().filter(t -> "SUCCESS".equals(t.getStatus())).count());
        aiTaskStats.put("recentTasks", aiTasks.stream().limit(5).map(t -> {
            Map<String, Object> tk = new HashMap<>();
            tk.put("goal", t.getGoal());
            tk.put("taskType", t.getTaskType());
            tk.put("agentType", t.getAgentType());
            tk.put("status", t.getStatus());
            tk.put("createdAt", t.getCreatedAt());
            return tk;
        }).collect(Collectors.toList()));
        plan.put("aiTaskStats", aiTaskStats);

        return plan;
    }

    private Map<String, Object> buildLearningStats(Long userId) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("activePlans", learningPlanRepository.findByUserIdAndStatus(userId, "ACTIVE").size());
        stats.put("todayMinutes", learningSessionRepository.sumDurationByUserIdAndDate(userId, LocalDate.now()));
        stats.put("weekMinutes", learningSessionRepository.sumDurationByUserIdAndDateBetween(
                userId, LocalDate.now().minusDays(7), LocalDate.now()));
        stats.put("totalSessions", learningSessionRepository.findByUserId(userId).size());
        stats.put("weeklyAvgFocus", learningSessionRepository.avgFocusLevelByUserIdAndDateBetween(
                userId, LocalDate.now().minusWeeks(1), LocalDate.now()));
        return stats;
    }

    private List<Map<String, Object>> buildRecentSessions(Long userId) {
        return learningSessionRepository.findByUserId(userId).stream()
                .sorted(Comparator.comparing(LearningSession::getCreatedAt).reversed())
                .limit(5)
                .map(session -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", session.getId());
                    map.put("subject", session.getSubject());
                    map.put("durationMinutes", session.getDurationMinutes());
                    map.put("sessionDate", session.getSessionDate());
                    map.put("focusLevel", session.getFocusLevel());
                    return map;
                })
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildTodayEvents(Long userId) {
        return scheduleEventRepository.findByUserIdAndEventDate(userId, LocalDate.now()).stream()
                .sorted(Comparator.comparing(ScheduleEvent::getStartTime))
                .map(event -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", event.getId());
                    map.put("title", event.getTitle());
                    map.put("startTime", event.getStartTime());
                    map.put("endTime", event.getEndTime());
                    map.put("completed", event.getIsCompleted());
                    map.put("priority", event.getPriority());
                    return map;
                })
                .collect(Collectors.toList());
    }

    private Map<String, Object> buildCareerStats(Long userId) {
        Map<String, Object> stats = new HashMap<>();
        careerProfileRepository.findByUserId(userId).ifPresent(profile -> {
            stats.put("hasProfile", true);
            stats.put("title", profile.getTitle());
        });
        stats.put("totalApplications", jobApplicationRepository.findByUserId(userId).size());
        stats.put("pendingApplications", jobApplicationRepository.findByUserIdAndStatus(userId, "PENDING").size());
        return stats;
    }
}
