package com.focusos.controller;

import com.focusos.dto.response.ApiResponse;
import com.focusos.entity.CareerGrowthPlan;
import com.focusos.entity.User;
import com.focusos.service.CareerGrowthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sprint 8-B: 职业成长规划 Controller
 * <p>
 * 职业成长规划 API：
 * 1. POST /career/growth                            — 生成职业成长规划
 * 2. GET  /career/growth/{planId}                   — 获取指定成长规划（含完整 skillGaps/roadmap/weeklyTasks/projects）
 * 3. GET  /career/growth                            — 查询用户全部成长规划历史
 * 4. GET  /resume/versions/{versionId}/growth       — 查询指定简历版本的成长规划历史
 * <p>
 * 与 CareerController 共享 /career 前缀，使用 /growth 子路径区分。
 */
@RestController
@RequestMapping("/career")
@RequiredArgsConstructor
public class CareerGrowthController {

    private final CareerGrowthService careerGrowthService;

    /**
     * 生成职业成长规划
     * <p>
     * 请求体：
     * {
     *   "resumeVersionId": 123,        // 必填，被评估的简历版本 ID
     *   "evaluationId": 456,           // 可选，来源 ResumeEvaluationReport ID（提供时获取评分上下文）
     *   "careerReportId": 789,         // 可选，来源 CareerAnalysisReport ID（提供时从中获取 JD）
     *   "jobDescription": "..."        // 可选，原始 JD（其他来源为空时使用）
     * }
     * <p>
     * 返回：planId + 当前等级 + 完整规划数据（前端可直接渲染，无需二次请求）
     */
    @PostMapping("/growth")
    public ApiResponse<Map<String, Object>> generateGrowthPlan(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, Object> request) {

        Long resumeVersionId = getAsLong(request.get("resumeVersionId"));
        Long evaluationId = getAsLong(request.get("evaluationId"));
        Long careerReportId = getAsLong(request.get("careerReportId"));
        String jobDescription = getAsString(request.get("jobDescription"));

        if (resumeVersionId == null) {
            return ApiResponse.error("resumeVersionId 不能为空");
        }

        CareerGrowthPlan plan = careerGrowthService.generate(
                user.getId(), resumeVersionId, evaluationId, careerReportId, jobDescription);

        // 返回 planId + 完整结构化规划数据（前端可直接渲染 roadmap/weeklyTasks/projects 等）
        Map<String, Object> data = new HashMap<>();
        data.put("planId", plan.getId());
        data.put("currentLevel", plan.getCurrentLevel());
        data.put("targetPosition", plan.getTargetPosition());
        data.put("company", plan.getCompany());
        data.put("status", plan.getStatus());
        data.put("createdAt", plan.getCreatedAt());
        // 完整规划明细（含 skillGaps / roadmap / weeklyTasks / projects 等）
        data.put("plan", careerGrowthService.parseGrowthJson(plan.getGrowthPlanJson()));

        return ApiResponse.success("职业成长规划生成完成", data);
    }

    /**
     * 获取指定成长规划（含完整规划明细）
     */
    @GetMapping("/growth/{planId}")
    public ApiResponse<Map<String, Object>> getGrowthPlan(
            @AuthenticationPrincipal User user,
            @PathVariable Long planId) {

        CareerGrowthPlan plan = careerGrowthService.getPlan(user.getId(), planId);

        Map<String, Object> data = toSummaryMap(plan);
        // 完整规划明细
        data.put("plan", careerGrowthService.parseGrowthJson(plan.getGrowthPlanJson()));
        return ApiResponse.success(data);
    }

    /**
     * 查询用户全部成长规划历史（最新优先）
     */
    @GetMapping("/growth")
    public ApiResponse<List<Map<String, Object>>> listGrowthPlans(@AuthenticationPrincipal User user) {
        List<CareerGrowthPlan> plans = careerGrowthService.listPlans(user.getId());
        return ApiResponse.success(plans.stream().map(this::toSummaryMap).toList());
    }

    /**
     * 查询指定简历版本的成长规划历史（最新优先）
     */
    @GetMapping("/versions/{versionId}/growth")
    public ApiResponse<List<Map<String, Object>>> listGrowthPlansByVersion(
            @AuthenticationPrincipal User user,
            @PathVariable Long versionId) {
        List<CareerGrowthPlan> plans =
                careerGrowthService.listPlansByVersion(user.getId(), versionId);
        return ApiResponse.success(plans.stream().map(this::toSummaryMap).toList());
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 将实体转换为摘要 Map（列表视图，不含 growthPlanJson 大字段）
     */
    private Map<String, Object> toSummaryMap(CareerGrowthPlan plan) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", plan.getId());
        data.put("userId", plan.getUserId());
        data.put("resumeVersionId", plan.getResumeVersionId());
        data.put("evaluationId", plan.getEvaluationId());
        data.put("targetPosition", plan.getTargetPosition());
        data.put("company", plan.getCompany());
        data.put("currentLevel", plan.getCurrentLevel());
        data.put("status", plan.getStatus());
        data.put("createdAt", plan.getCreatedAt());
        data.put("updatedAt", plan.getUpdatedAt());
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
