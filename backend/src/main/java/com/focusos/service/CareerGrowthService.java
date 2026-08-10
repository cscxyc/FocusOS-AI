package com.focusos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.focusos.agent.CareerGrowthAgent;
import com.focusos.agent.LLMCallContext;
import com.focusos.agent.LLMJsonSanitizer;
import com.focusos.agent.PersonalProfileService;
import com.focusos.agent.UserProfileContext;
import com.focusos.dto.response.CareerGrowthResponse;
import com.focusos.entity.CareerAnalysisReport;
import com.focusos.entity.CareerGrowthPlan;
import com.focusos.entity.ResumeEvaluationReport;
import com.focusos.entity.ResumeVersion;
import com.focusos.exception.BusinessException;
import com.focusos.exception.ResourceNotFoundException;
import com.focusos.repository.CareerAnalysisReportRepository;
import com.focusos.repository.CareerGrowthPlanRepository;
import com.focusos.repository.ResumeEvaluationReportRepository;
import com.focusos.repository.ResumeVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Sprint 8-B: 职业成长规划服务
 * <p>
 * 职责：
 * 1. 根据 resumeVersionId 查询简历
 * 2. 根据 evaluationId 查询 ResumeEvaluationReport（评分 + 评估明细）
 * 3. 从 CareerAnalysisReport 或请求参数获取 JD
 * 4. 调用 PersonalProfileService 检索用户画像
 * 5. 调用 CareerGrowthAgent 生成成长规划
 * 6. 保存 CareerGrowthPlan
 * <p>
 * 分层：Controller → CareerGrowthService → CareerGrowthAgent → Repository
 * <p>
 * LLM 调用监控：所有 LLM 调用通过 LLMCallContext 设置 agentType="career_growth"，
 * 由 LoggingChatLanguageModel 装饰器自动写入 llm_call_logs。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CareerGrowthService {

    private final CareerGrowthAgent careerGrowthAgent;
    private final PersonalProfileService personalProfileService;
    private final ResumeVersionRepository resumeVersionRepository;
    private final ResumeEvaluationReportRepository evaluationRepository;
    private final CareerAnalysisReportRepository careerReportRepository;
    private final CareerGrowthPlanRepository growthPlanRepository;
    private final LLMJsonSanitizer jsonSanitizer;
    private final ObjectMapper objectMapper;

    /**
     * 生成职业成长规划
     * <p>
     * 流程：
     * 1. 查询 ResumeVersion（所有权校验）
     * 2. 查询 ResumeEvaluationReport（获取评分 + 评估明细，可选但推荐）
     * 3. 查询 JD：优先从 CareerAnalysisReport 获取，其次从 ResumeEvaluationReport 关联，最后使用请求中的 jobDescription
     * 4. 设置 LLMCallContext（agentType=career_growth）
     * 5. 检索用户画像（PersonalProfileService）
     * 6. 调用 CareerGrowthAgent.generateGrowthPlan
     * 7. 解析规划结果，保存 CareerGrowthPlan
     *
     * @param userId           用户 ID
     * @param resumeVersionId  简历版本 ID（必填）
     * @param evaluationId     ResumeEvaluationReport ID（可选，提供时获取评分上下文）
     * @param careerReportId   CareerAnalysisReport ID（可选，提供时从中获取 JD）
     * @param jobDescription   原始 JD（可选，其他来源为空时使用）
     * @return 创建的 CareerGrowthPlan
     */
    @Transactional
    public CareerGrowthPlan generate(Long userId, Long resumeVersionId,
                                      Long evaluationId, Long careerReportId,
                                      String jobDescription) {
        log.info("Sprint 8-B: generate career growth plan, userId={}, resumeVersionId={}, evaluationId={}, careerReportId={}",
                userId, resumeVersionId, evaluationId, careerReportId);

        // 1. 查询简历版本（所有权校验）
        ResumeVersion version = getOwnedVersion(userId, resumeVersionId);
        if (version.getContent() == null || version.getContent().isBlank()) {
            throw new BusinessException("简历内容为空，无法生成成长规划");
        }

        // 2. 查询 ResumeEvaluationReport（提供时获取评分 + 评估明细）
        Integer evaluationScore = null;
        String evaluationJson = null;
        String jobTitle = version.getTargetPosition();
        String company = null;

        if (evaluationId != null) {
            ResumeEvaluationReport evalReport = evaluationRepository.findById(evaluationId)
                    .orElseThrow(() -> new ResourceNotFoundException("简历评估报告", evaluationId));
            if (!evalReport.getUserId().equals(userId)) {
                throw new BusinessException("无权访问该评估报告");
            }
            evaluationScore = evalReport.getScore();
            evaluationJson = evalReport.getEvaluationJson();
            if (evalReport.getJobTitle() != null) jobTitle = evalReport.getJobTitle();
            if (evalReport.getCompany() != null) company = evalReport.getCompany();
        }

        // 3. 解析 JD（优先级：careerReportId → ResumeEvaluationReport.careerReportId → jobDescription）
        String jd = jobDescription;
        Long resolvedCareerReportId = careerReportId;

        if (careerReportId != null) {
            CareerAnalysisReport report = careerReportRepository.findById(careerReportId)
                    .orElseThrow(() -> new ResourceNotFoundException("Career报告", careerReportId));
            if (!report.getUserId().equals(userId)) {
                throw new BusinessException("无权访问该 Career 报告");
            }
            if (jd == null || jd.isBlank()) {
                jd = report.getJobDescription();
            }
            if (report.getJobTitle() != null) jobTitle = report.getJobTitle();
            if (report.getCompany() != null) company = report.getCompany();
        }

        if ((jd == null || jd.isBlank()) && evaluationId != null) {
            // 尝试从 ResumeEvaluationReport 关联的 CareerAnalysisReport 获取
            ResumeEvaluationReport evalReport = evaluationRepository.findById(evaluationId).orElse(null);
            if (evalReport != null && evalReport.getCareerReportId() != null) {
                CareerAnalysisReport car = careerReportRepository.findById(evalReport.getCareerReportId()).orElse(null);
                if (car != null && car.getUserId().equals(userId)) {
                    if (jd == null || jd.isBlank()) jd = car.getJobDescription();
                    if (resolvedCareerReportId == null) resolvedCareerReportId = car.getId();
                    if (car.getJobTitle() != null && jobTitle == null) jobTitle = car.getJobTitle();
                    if (car.getCompany() != null && company == null) company = car.getCompany();
                }
            }
        }

        if (jd == null || jd.isBlank()) {
            log.warn("No JD provided for career growth plan, will generate generic plan based on resume content only");
        }

        // 4. 设置 LLM 调用上下文（写入 llm_call_logs，agentType=career_growth）
        LLMCallContext.set(userId, null, "career_growth");
        try {
            // 5. 检索用户画像（Personal RAG，用于真实性核查）
            String jdForProfile = (jd == null || jd.isBlank()) ? version.getContent() : jd;
            UserProfileContext profileContext = personalProfileService.retrieveCareerProfile(userId, jdForProfile);

            // 6. 调用 CareerGrowthAgent 生成规划
            String growthJson = careerGrowthAgent.generateGrowthPlan(
                    jd, version.getContent(), evaluationScore, evaluationJson, profileContext);

            // 7. 解析规划结果并保存
            CareerGrowthResponse responseDto = parseGrowthResponse(growthJson);
            CareerGrowthPlan plan = buildPlanEntity(userId, resumeVersionId, evaluationId,
                    jobTitle, company, responseDto, growthJson);

            CareerGrowthPlan saved = growthPlanRepository.save(plan);
            log.info("CareerGrowthPlan created: id={}, userId={}, versionId={}, currentLevel={}",
                    saved.getId(), userId, resumeVersionId, saved.getCurrentLevel());
            return saved;
        } finally {
            LLMCallContext.clear();
        }
    }

    /**
     * 获取指定成长规划
     */
    public CareerGrowthPlan getPlan(Long userId, Long planId) {
        CareerGrowthPlan plan = growthPlanRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("职业成长规划", planId));
        if (!plan.getUserId().equals(userId)) {
            throw new BusinessException("无权访问该成长规划");
        }
        return plan;
    }

    /**
     * 查询用户全部成长规划（最新优先）
     */
    public List<CareerGrowthPlan> listPlans(Long userId) {
        return growthPlanRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * 查询指定简历版本的成长规划历史（最新优先）
     */
    public List<CareerGrowthPlan> listPlansByVersion(Long userId, Long resumeVersionId) {
        // 先校验简历版本所有权
        getOwnedVersion(userId, resumeVersionId);
        return growthPlanRepository.findByUserIdAndResumeVersionIdOrderByCreatedAtDesc(userId, resumeVersionId);
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 解析成长规划 JSON 为 DTO（用于提取字段写入实体）
     */
    private CareerGrowthResponse parseGrowthResponse(String growthJson) {
        if (growthJson == null || growthJson.isBlank()) {
            return null;
        }
        try {
            // 规划 JSON 已经由 Agent 通过 LLMJsonSanitizer 序列化，这里直接解析
            return objectMapper.readValue(growthJson, CareerGrowthResponse.class);
        } catch (Exception e) {
            // 兜底：再次走 sanitizer 清洗
            log.warn("Failed to parse growth JSON directly, retry via sanitizer: {}", e.getMessage());
            return jsonSanitizer.sanitizeToObject(growthJson, CareerGrowthResponse.class);
        }
    }

    /**
     * 构建 CareerGrowthPlan 实体
     */
    private CareerGrowthPlan buildPlanEntity(Long userId, Long resumeVersionId, Long evaluationId,
                                              String jobTitle, String company,
                                              CareerGrowthResponse dto, String growthJson) {
        CareerGrowthPlan plan = new CareerGrowthPlan();
        plan.setUserId(userId);
        plan.setResumeVersionId(resumeVersionId);
        plan.setEvaluationId(evaluationId);
        plan.setTargetPosition(jobTitle);
        plan.setCompany(company);
        plan.setGrowthPlanJson(growthJson);
        plan.setStatus("ACTIVE");

        if (dto != null) {
            plan.setCurrentLevel(dto.getCurrentLevel());
        } else {
            plan.setCurrentLevel("未评级");
        }
        return plan;
    }

    private ResumeVersion getOwnedVersion(Long userId, Long versionId) {
        ResumeVersion version = resumeVersionRepository.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("简历版本", versionId));
        if (!version.getUserId().equals(userId)) {
            throw new BusinessException("无权访问该简历版本");
        }
        return version;
    }

    /**
     * 将成长规划的 JSON 字段解析为 Map（供 Controller 返回完整结构化数据）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> parseGrowthJson(String growthPlanJson) {
        if (growthPlanJson == null || growthPlanJson.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(growthPlanJson, Map.class);
        } catch (Exception e) {
            String sanitized = jsonSanitizer.sanitize(growthPlanJson);
            try {
                return objectMapper.readValue(sanitized, Map.class);
            } catch (Exception ex) {
                log.warn("Failed to parse growthPlanJson: {}", ex.getMessage());
                return Map.of();
            }
        }
    }
}
