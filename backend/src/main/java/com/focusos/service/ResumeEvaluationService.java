package com.focusos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.focusos.agent.LLMCallContext;
import com.focusos.agent.LLMJsonSanitizer;
import com.focusos.agent.PersonalProfileService;
import com.focusos.agent.ResumeEvaluatorAgent;
import com.focusos.agent.UserProfileContext;
import com.focusos.dto.response.ResumeEvaluationResponse;
import com.focusos.entity.CareerAnalysisReport;
import com.focusos.entity.ResumeEvaluationReport;
import com.focusos.entity.ResumeVersion;
import com.focusos.exception.BusinessException;
import com.focusos.exception.ResourceNotFoundException;
import com.focusos.repository.CareerAnalysisReportRepository;
import com.focusos.repository.ResumeEvaluationReportRepository;
import com.focusos.repository.ResumeVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Sprint 8-A: 简历评估服务
 * <p>
 * 职责：
 * 1. 根据 resumeVersionId 查询简历
 * 2. 根据 careerReportId 查询 JD（或使用请求中的 jobDescription）
 * 3. 调用 PersonalProfileService 检索用户画像
 * 4. 调用 ResumeEvaluatorAgent 生成评估
 * 5. 保存 ResumeEvaluationReport
 * <p>
 * 分层：Controller → ResumeEvaluationService → ResumeEvaluatorAgent → Repository
 * <p>
 * LLM 调用监控：所有 LLM 调用通过 LLMCallContext 设置 agentType="resume_evaluator"，
 * 由 LoggingChatLanguageModel 装饰器自动写入 llm_call_logs。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeEvaluationService {

    private final ResumeEvaluatorAgent resumeEvaluatorAgent;
    private final PersonalProfileService personalProfileService;
    private final ResumeVersionRepository resumeVersionRepository;
    private final CareerAnalysisReportRepository careerReportRepository;
    private final ResumeEvaluationReportRepository evaluationRepository;
    private final LLMJsonSanitizer jsonSanitizer;
    private final ObjectMapper objectMapper;

    /**
     * 执行简历评估
     * <p>
     * 流程：
     * 1. 查询 ResumeVersion（所有权校验）
     * 2. 查询 JD：优先从 CareerAnalysisReport 获取，其次使用请求中的 jobDescription
     * 3. 设置 LLMCallContext（agentType=resume_evaluator）
     * 4. 检索用户画像（PersonalProfileService）
     * 5. 调用 ResumeEvaluatorAgent.evaluateResume
     * 6. 解析评估结果，保存 ResumeEvaluationReport
     *
     * @param userId          用户 ID
     * @param resumeVersionId 简历版本 ID
     * @param careerReportId  CareerAnalysisReport ID（可选，提供时从中获取 JD）
     * @param jobDescription  原始 JD（可选，careerReportId 为空时使用）
     * @return 创建的 ResumeEvaluationReport
     */
    @Transactional
    public ResumeEvaluationReport evaluate(Long userId, Long resumeVersionId,
                                            Long careerReportId, String jobDescription) {
        log.info("Sprint 8-A: evaluate resume, userId={}, resumeVersionId={}, careerReportId={}",
                userId, resumeVersionId, careerReportId);

        // 1. 查询简历版本（所有权校验）
        ResumeVersion version = getOwnedVersion(userId, resumeVersionId);
        if (version.getContent() == null || version.getContent().isBlank()) {
            throw new BusinessException("简历内容为空，无法评估");
        }

        // 2. 解析 JD（优先从 CareerAnalysisReport 获取）
        String jd = jobDescription;
        String jobTitle = version.getTargetPosition();
        String company = null;
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

        if (jd == null || jd.isBlank()) {
            throw new BusinessException("未提供 JD，且 Career 报告中无 JD，无法评估（请提供 careerReportId 或 jobDescription）");
        }

        // 3. 设置 LLM 调用上下文（写入 llm_call_logs，agentType=resume_evaluator）
        LLMCallContext.set(userId, null, "resume_evaluator");
        try {
            // 4. 检索用户画像（Personal RAG，用于真实性核查）
            UserProfileContext profileContext = personalProfileService.retrieveCareerProfile(userId, jd);

            // 5. 调用 ResumeEvaluatorAgent 生成评估
            String evaluationJson = resumeEvaluatorAgent.evaluateResume(jd, version.getContent(), profileContext);

            // 6. 解析评估结果并保存
            ResumeEvaluationResponse responseDto = parseEvaluationResponse(evaluationJson);
            ResumeEvaluationReport report = buildReportEntity(userId, resumeVersionId,
                    resolvedCareerReportId, jobTitle, company, responseDto, evaluationJson);

            ResumeEvaluationReport saved = evaluationRepository.save(report);
            log.info("ResumeEvaluationReport created: id={}, userId={}, versionId={}, score={}",
                    saved.getId(), userId, resumeVersionId, saved.getScore());
            return saved;
        } finally {
            LLMCallContext.clear();
        }
    }

    /**
     * 获取指定评估报告
     */
    public ResumeEvaluationReport getEvaluation(Long userId, Long evaluationId) {
        ResumeEvaluationReport report = evaluationRepository.findById(evaluationId)
                .orElseThrow(() -> new ResourceNotFoundException("简历评估报告", evaluationId));
        if (!report.getUserId().equals(userId)) {
            throw new BusinessException("无权访问该评估报告");
        }
        return report;
    }

    /**
     * 查询用户全部评估报告（最新优先）
     */
    public List<ResumeEvaluationReport> listEvaluations(Long userId) {
        return evaluationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * 查询指定简历版本的评估历史（最新优先）
     */
    public List<ResumeEvaluationReport> listEvaluationsByVersion(Long userId, Long resumeVersionId) {
        // 先校验简历版本所有权
        getOwnedVersion(userId, resumeVersionId);
        return evaluationRepository.findByUserIdAndResumeVersionIdOrderByCreatedAtDesc(userId, resumeVersionId);
    }

    /**
     * 查询指定简历版本的全部评估历史（不限用户，用于内部调用）
     */
    public List<ResumeEvaluationReport> listEvaluationsByVersion(Long resumeVersionId) {
        return evaluationRepository.findByResumeVersionIdOrderByCreatedAtDesc(resumeVersionId);
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 解析评估 JSON 为 DTO（用于提取字段写入实体）
     */
    private ResumeEvaluationResponse parseEvaluationResponse(String evaluationJson) {
        if (evaluationJson == null || evaluationJson.isBlank()) {
            return null;
        }
        try {
            // 评估 JSON 已经由 Agent 通过 LLMJsonSanitizer 序列化，这里直接解析
            return objectMapper.readValue(evaluationJson, ResumeEvaluationResponse.class);
        } catch (Exception e) {
            // 兜底：再次走 sanitizer 清洗
            log.warn("Failed to parse evaluation JSON directly, retry via sanitizer: {}", e.getMessage());
            return jsonSanitizer.sanitizeToObject(evaluationJson, ResumeEvaluationResponse.class);
        }
    }

    /**
     * 构建 ResumeEvaluationReport 实体
     */
    private ResumeEvaluationReport buildReportEntity(Long userId, Long resumeVersionId,
                                                      Long careerReportId, String jobTitle, String company,
                                                      ResumeEvaluationResponse dto, String evaluationJson) {
        ResumeEvaluationReport report = new ResumeEvaluationReport();
        report.setUserId(userId);
        report.setResumeVersionId(resumeVersionId);
        report.setCareerReportId(careerReportId);
        report.setJobTitle(jobTitle);
        report.setCompany(company);
        report.setEvaluationJson(evaluationJson);

        if (dto != null) {
            report.setScore(dto.getScore());
            report.setMatchScore(dto.getMatchScore());
            report.setAtsScore(dto.getAtsScore());
            report.setStarScore(dto.getStarScore());
            report.setCompletenessScore(dto.getCompletenessScore());
            report.setStrengths(toJsonString(dto.getStrengths()));
            report.setWeaknesses(toJsonString(dto.getWeaknesses()));
            report.setSuggestions(toJsonString(dto.getSuggestions()));
        } else {
            report.setScore(0);
            report.setMatchScore(0);
            report.setAtsScore(0);
            report.setStarScore(0);
            report.setCompletenessScore(0);
            report.setStrengths("[]");
            report.setWeaknesses("[\"评估解析失败\"]");
            report.setSuggestions("[]");
        }
        return report;
    }

    /**
     * 将 List 序列化为 JSON 字符串（用于存储到实体字段）
     */
    private String toJsonString(List<String> list) {
        if (list == null || list.isEmpty()) return "[]";
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            log.warn("Failed to serialize list to JSON: {}", e.getMessage());
            return "[]";
        }
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
     * 将评估报告的 JSON 字段解析为 Map（供 Controller 返回完整结构化数据）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> parseEvaluationJson(String evaluationJson) {
        if (evaluationJson == null || evaluationJson.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(evaluationJson, Map.class);
        } catch (Exception e) {
            String sanitized = jsonSanitizer.sanitize(evaluationJson);
            try {
                return objectMapper.readValue(sanitized, Map.class);
            } catch (Exception ex) {
                log.warn("Failed to parse evaluationJson: {}", ex.getMessage());
                return Map.of();
            }
        }
    }
}
