package com.focusos.controller;

import com.focusos.dto.response.ApiResponse;
import com.focusos.entity.ResumeEvaluationReport;
import com.focusos.entity.User;
import com.focusos.service.ResumeEvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sprint 8-A: 简历评估 Controller
 * <p>
 * 简历 AI 质量评分 API：
 * 1. POST /resume/evaluate                          — 执行简历评估
 * 2. GET  /resume/evaluations/{evaluationId}        — 获取指定评估结果（含完整评分明细）
 * 3. GET  /resume/evaluations                       — 查询用户全部评估历史
 * 4. GET  /resume/versions/{versionId}/evaluations  — 查询指定简历版本的评估历史
 * <p>
 * 与 ResumeController 共享 /resume 前缀，但使用不同的子路径，无冲突。
 */
@RestController
@RequestMapping("/resume")
@RequiredArgsConstructor
public class ResumeEvaluationController {

    private final ResumeEvaluationService resumeEvaluationService;

    /**
     * 执行简历评估
     * <p>
     * 请求体：
     * {
     *   "resumeVersionId": 123,        // 必填，被评估的简历版本 ID
     *   "careerReportId": 456,         // 可选，来源 CareerAnalysisReport ID（提供时从中获取 JD）
     *   "jobDescription": "..."        // 可选，原始 JD（careerReportId 为空时使用）
     * }
     * <p>
     * 返回：evaluationId + 完整评估结果（前端可直接展示评分）
     */
    @PostMapping("/evaluate")
    public ApiResponse<Map<String, Object>> evaluate(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, Object> request) {

        Long resumeVersionId = getAsLong(request.get("resumeVersionId"));
        Long careerReportId = getAsLong(request.get("careerReportId"));
        String jobDescription = getAsString(request.get("jobDescription"));

        if (resumeVersionId == null) {
            return ApiResponse.error("resumeVersionId 不能为空");
        }
        if (careerReportId == null && (jobDescription == null || jobDescription.isBlank())) {
            return ApiResponse.error("必须提供 careerReportId 或 jobDescription");
        }

        ResumeEvaluationReport report = resumeEvaluationService.evaluate(
                user.getId(), resumeVersionId, careerReportId, jobDescription);

        // 返回 evaluationId + 完整结构化评估数据（前端可直接渲染，无需二次请求）
        Map<String, Object> data = new HashMap<>();
        data.put("evaluationId", report.getId());
        data.put("score", report.getScore());
        data.put("matchScore", report.getMatchScore());
        data.put("atsScore", report.getAtsScore());
        data.put("starScore", report.getStarScore());
        data.put("completenessScore", report.getCompletenessScore());
        data.put("jobTitle", report.getJobTitle());
        data.put("company", report.getCompany());
        // 完整评估明细（含 keywordMatches / sectionScores / suggestions 等）
        data.put("evaluation", resumeEvaluationService.parseEvaluationJson(report.getEvaluationJson()));

        return ApiResponse.success("简历评估完成", data);
    }

    /**
     * 获取指定评估结果（含完整评分明细）
     */
    @GetMapping("/evaluations/{evaluationId}")
    public ApiResponse<Map<String, Object>> getEvaluation(
            @AuthenticationPrincipal User user,
            @PathVariable Long evaluationId) {

        ResumeEvaluationReport report = resumeEvaluationService.getEvaluation(user.getId(), evaluationId);

        Map<String, Object> data = toSummaryMap(report);
        // 完整评估明细
        data.put("evaluation", resumeEvaluationService.parseEvaluationJson(report.getEvaluationJson()));
        return ApiResponse.success(data);
    }

    /**
     * 查询用户全部评估历史
     */
    @GetMapping("/evaluations")
    public ApiResponse<List<Map<String, Object>>> listEvaluations(@AuthenticationPrincipal User user) {
        List<ResumeEvaluationReport> reports = resumeEvaluationService.listEvaluations(user.getId());
        return ApiResponse.success(reports.stream().map(this::toSummaryMap).toList());
    }

    /**
     * 查询指定简历版本的评估历史
     */
    @GetMapping("/versions/{versionId}/evaluations")
    public ApiResponse<List<Map<String, Object>>> listEvaluationsByVersion(
            @AuthenticationPrincipal User user,
            @PathVariable Long versionId) {
        List<ResumeEvaluationReport> reports =
                resumeEvaluationService.listEvaluationsByVersion(user.getId(), versionId);
        return ApiResponse.success(reports.stream().map(this::toSummaryMap).toList());
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 将实体转换为摘要 Map（列表视图，不含 evaluationJson 大字段）
     */
    private Map<String, Object> toSummaryMap(ResumeEvaluationReport report) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", report.getId());
        data.put("userId", report.getUserId());
        data.put("resumeVersionId", report.getResumeVersionId());
        data.put("careerReportId", report.getCareerReportId());
        data.put("jobTitle", report.getJobTitle());
        data.put("company", report.getCompany());
        data.put("score", report.getScore());
        data.put("matchScore", report.getMatchScore());
        data.put("atsScore", report.getAtsScore());
        data.put("starScore", report.getStarScore());
        data.put("completenessScore", report.getCompletenessScore());
        data.put("createdAt", report.getCreatedAt());
        data.put("updatedAt", report.getUpdatedAt());
        return data;
    }

    private Long getAsLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        try {
            return Long.parseLong(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String getAsString(Object v) {
        return v != null ? v.toString() : null;
    }
}
