package com.focusos.controller;

import com.focusos.dto.request.AnalyzeJDRequest;
import com.focusos.dto.request.CreateCareerProfileRequest;
import com.focusos.dto.response.ApiResponse;
import com.focusos.dto.response.CareerProfileResponse;
import com.focusos.dto.response.JobApplicationResponse;
import com.focusos.entity.CareerAnalysisReport;
import com.focusos.entity.User;
import com.focusos.service.CareerService;
import com.focusos.service.ResumeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/career")
@RequiredArgsConstructor
public class CareerController {

    private final CareerService careerService;
    private final ResumeService resumeService;

    @PostMapping("/profile")
    public ApiResponse<CareerProfileResponse> createProfile(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateCareerProfileRequest request) {
        return ApiResponse.success("职业档案创建成功", careerService.createProfile(user.getId(), request));
    }

    @GetMapping("/profile")
    public ApiResponse<CareerProfileResponse> getProfile(@AuthenticationPrincipal User user) {
        return ApiResponse.success(careerService.getProfile(user.getId()));
    }

    @PutMapping("/profile")
    public ApiResponse<CareerProfileResponse> updateProfile(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateCareerProfileRequest request) {
        return ApiResponse.success("职业档案更新成功", careerService.updateProfile(user.getId(), request));
    }

    @PostMapping("/jd-analyze")
    public ApiResponse<String> analyzeJD(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AnalyzeJDRequest request) {
        return ApiResponse.success("职位分析完成", careerService.analyzeJD(user.getId(), request));
    }

    @PostMapping("/resume-optimize")
    public ApiResponse<String> optimizeResume(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, String> request) {
        String jobDescription = request.get("jobDescription");
        return ApiResponse.success("简历优化完成", careerService.optimizeResume(user.getId(), jobDescription));
    }

    /**
     * Sprint 5-B: 基于个人知识库的职业方向推荐
     * "根据我的经历，我适合哪些AI岗位？"
     */
    @PostMapping("/recommend")
    public ApiResponse<String> recommendCareerDirections(@AuthenticationPrincipal User user) {
        return ApiResponse.success("职业方向推荐完成", careerService.recommendCareerDirections(user.getId()));
    }

    /**
     * Sprint 5-B: 基于个人知识库的简历优化（检索用户真实经历）
     * "我的简历应该怎么修改？"
     */
    @PostMapping("/resume-optimize-profile")
    public ApiResponse<String> optimizeResumeWithProfile(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, String> request) {
        String jobDescription = request.get("jobDescription");
        return ApiResponse.success("简历优化完成（基于个人知识库）",
                careerService.optimizeResumeWithProfile(user.getId(), jobDescription));
    }

    @GetMapping("/applications")
    public ApiResponse<List<JobApplicationResponse>> getApplications(@AuthenticationPrincipal User user) {
        return ApiResponse.success(careerService.getJobApplications(user.getId()));
    }

    @GetMapping("/applications/{status}")
    public ApiResponse<List<JobApplicationResponse>> getApplicationsByStatus(
            @AuthenticationPrincipal User user,
            @PathVariable String status) {
        return ApiResponse.success(careerService.getJobApplicationsByStatus(user.getId(), status));
    }

    // ===== Sprint 7-A: Career Workflow =====

    /**
     * Sprint 7-A: 启动 Career Workflow（5步 DAG 异步）
     * <p>
     * 请求体：{ "jobDescription": "...", "jobTitle": "...", "company": "..." }
     * 返回：{ "workflowId": "career-xxxxxxxx", "status": "STARTED", "sseEndpoint": "/api/workflow/career-xxxxxxxx/events" }
     * <p>
     * 前端通过 SSE 订阅 sseEndpoint 获取实时进度，完成后通过 /career/reports/by-workflow/{workflowId} 获取报告。
     */
    @PostMapping("/analyze-workflow")
    public ApiResponse<Map<String, Object>> startCareerWorkflow(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, String> request) {
        String jobDescription = request.get("jobDescription");
        String jobTitle = request.get("jobTitle");
        String company = request.get("company");

        if (jobDescription == null || jobDescription.isBlank()) {
            return ApiResponse.error("职位描述不能为空");
        }

        long t0 = System.currentTimeMillis();
        String workflowId = careerService.startCareerWorkflow(user.getId(), jobDescription, jobTitle, company);
        long elapsed = System.currentTimeMillis() - t0;

        return ApiResponse.success("Career 分析已启动", Map.of(
                "workflowId", workflowId,
                "status", "STARTED",
                "sseEndpoint", "/api/workflow/" + workflowId + "/events",
                "startElapsedMs", elapsed
        ));
    }

    /**
     * Sprint 7-A: 获取用户全部 Career 报告（历史查看）
     */
    @GetMapping("/reports")
    public ApiResponse<List<CareerAnalysisReport>> getCareerReports(@AuthenticationPrincipal User user) {
        return ApiResponse.success(careerService.getCareerReports(user.getId()));
    }

    /**
     * Sprint 7-A: 获取指定 ID 的 Career 报告
     */
    @GetMapping("/reports/{reportId}")
    public ApiResponse<CareerAnalysisReport> getCareerReport(
            @AuthenticationPrincipal User user,
            @PathVariable Long reportId) {
        return ApiResponse.success(careerService.getCareerReport(user.getId(), reportId));
    }

    /**
     * Sprint 7-A: 通过 workflowId 获取 Career 报告（Workflow 完成后查询）
     */
    @GetMapping("/reports/by-workflow/{workflowId}")
    public ApiResponse<CareerAnalysisReport> getCareerReportByWorkflow(@PathVariable String workflowId) {
        CareerAnalysisReport report = careerService.getCareerReportByWorkflowId(workflowId);
        if (report == null) {
            return ApiResponse.error("报告尚未生成，工作流可能仍在进行中");
        }
        return ApiResponse.success(report);
    }

    /**
     * Sprint 7-C-A: Career Workflow → 用户确认 → 保存 ResumeVersion
     * <p>
     * 用户在查看 CareerAnalysisReport 的简历优化结果后，确认保存为 ResumeVersion。
     * 请求体：{ "reportId": 123, "versionName": "可选", "setActive": true }
     * <p>
     * 流程：
     * 1. 用户通过 Career Workflow 获得 CareerAnalysisReport（含 resumeSuggestions）
     * 2. 前端展示简历优化结果
     * 3. 用户点击"保存为简历版本"按钮
     * 4. 调用此接口 → resumeService.createVersionFromReport
     * 5. 系统将 resumeSuggestions JSON 转换为 Markdown 简历并保存为 ResumeVersion
     */
    @PostMapping("/reports/{reportId}/save-resume")
    public ApiResponse<com.focusos.dto.response.ResumeVersionResponse> saveResumeFromReport(
            @AuthenticationPrincipal User user,
            @PathVariable Long reportId,
            @RequestBody(required = false) Map<String, Object> request) {

        String versionName = request != null ? (String) request.get("versionName") : null;
        Boolean setActive = request != null ? (Boolean) request.get("setActive") : null;
        boolean active = setActive != null ? setActive : true;

        com.focusos.dto.response.ResumeVersionResponse response =
                resumeService.createVersionFromReport(user.getId(), reportId, versionName, active);
        return ApiResponse.success("简历版本已保存", response);
    }
}
