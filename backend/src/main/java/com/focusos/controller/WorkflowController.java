package com.focusos.controller;

import com.focusos.agent.AgentWorkflowService;
import com.focusos.agent.MasterAgent;
import com.focusos.dto.response.ApiResponse;
import com.focusos.dto.response.WorkflowResponse;
import com.focusos.entity.User;
import com.focusos.entity.WorkflowInstance;
import com.focusos.service.WorkflowScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sprint 6-A: Multi-Agent Workflow Controller（同步）
 * Sprint 6-B: 升级为异步启动 + 历史查询
 * Sprint 8-E: 增加 Workflow Scheduler 端点（submit/pause/resume/retry/instance）
 * <p>
 * Sprint 6-B 调用链：
 * POST /workflow/execute          → 立即返回 {workflowId, status:STARTED}
 * GET  /api/workflow/{id}/events  → SSE 订阅实时进度（见 WorkflowEventController）
 * GET  /workflow/{workflowId}     → 查询单个 workflow 详情（页面刷新恢复）
 * GET  /workflow/history          → 查询用户历史 workflows
 * <p>
 * Sprint 8-E 新增（基于 WorkflowScheduler）：
 * POST /workflow/submit           → 通过 Scheduler 提交 Workflow（持久化 + 入队）
 * POST /workflow/{id}/pause       → 暂停 Workflow
 * POST /workflow/{id}/resume      → 恢复 Workflow
 * POST /workflow/{id}/retry       → 重试失败的 Workflow
 * GET  /workflow/instances        → 查询用户持久化的 WorkflowInstance 列表
 */
@Slf4j
@RestController
@RequestMapping("/workflow")
@RequiredArgsConstructor
public class WorkflowController {

    private final MasterAgent masterAgent;
    private final AgentWorkflowService workflowService;
    private final WorkflowScheduler workflowScheduler;

    /**
     * Sprint 6-B: 异步启动 Workflow（立即返回 workflowId）
     * <p>
     * 前端拿到 workflowId 后通过 SSE 订阅 /api/workflow/{workflowId}/events 实时获取进度。
     */
    @PostMapping("/execute")
    public ApiResponse<Map<String, Object>> executeWorkflowAsync(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, String> request) {
        String goal = request.get("goal");
        if (goal == null || goal.isBlank()) {
            return ApiResponse.error("目标不能为空");
        }

        long t0 = System.currentTimeMillis();
        String workflowId = workflowService.startWorkflowAsync(user.getId(), goal);
        long elapsed = System.currentTimeMillis() - t0;

        log.info("Async workflow started: workflowId={}, goal={}, startElapsed={}ms",
                workflowId, goal, elapsed);

        return ApiResponse.success("工作流已启动", Map.of(
                "workflowId", workflowId,
                "status", "STARTED",
                "sseEndpoint", "/api/workflow/" + workflowId + "/events",
                "startElapsedMs", elapsed
        ));
    }

    /**
     * 获取用户工作流历史
     */
    @GetMapping("/history")
    public ApiResponse<List<WorkflowResponse>> getWorkflowHistory(@AuthenticationPrincipal User user) {
        return ApiResponse.success(workflowService.getUserWorkflows(user.getId()));
    }

    /**
     * Sprint 6-B: 查询单个 workflow 详情（页面刷新后恢复状态）
     */
    @GetMapping("/{workflowId}")
    public ApiResponse<WorkflowResponse> getWorkflow(@PathVariable String workflowId) {
        WorkflowResponse response = workflowService.getWorkflow(workflowId);
        if (response == null) {
            return ApiResponse.error("工作流不存在: " + workflowId);
        }
        return ApiResponse.success(response);
    }

    // ============================================================
    // Sprint 8-E: Workflow Scheduler 端点（持久化 + 调度）
    // ============================================================

    /**
     * Sprint 8-E: 通过 Scheduler 提交 Workflow。
     * <p>
     * 与 {@code /execute} 区别：本端点将 Workflow 状态持久化到 {@code workflow_instances} 表，
     * 支持 pause/resume/retry；执行结果通过 {@link WorkflowInstance} 状态字段查询。
     *
     * @param request 请求体：workflowType（必填），payload（可选）
     */
    @PostMapping("/submit")
    public ApiResponse<Map<String, Object>> submitWorkflow(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, String> request) {
        String workflowType = request.get("workflowType");
        if (workflowType == null || workflowType.isBlank()) {
            return ApiResponse.error("workflowType 不能为空");
        }
        String payload = request.get("payload");

        String workflowId = workflowScheduler.submitWorkflow(user.getId(), workflowType, payload);
        log.info("Sprint 8-E Workflow submitted via Scheduler: workflowId={}, type={}, userId={}",
                workflowId, workflowType, user.getId());

        Map<String, Object> data = new HashMap<>();
        data.put("workflowId", workflowId);
        data.put("workflowType", workflowType);
        data.put("status", "PENDING");
        data.put("userId", user.getId());
        return ApiResponse.success("Workflow 已提交", data);
    }

    /** Sprint 8-E: 暂停 Workflow（PAUSED 状态，需 resume 恢复） */
    @PostMapping("/{workflowId}/pause")
    public ApiResponse<Map<String, Object>> pauseWorkflow(@PathVariable String workflowId) {
        workflowScheduler.pauseWorkflow(workflowId);
        return ApiResponse.success("Workflow 已暂停", Map.of("workflowId", workflowId, "status", "PAUSED"));
    }

    /** Sprint 8-E: 恢复已暂停的 Workflow（重新入队执行） */
    @PostMapping("/{workflowId}/resume")
    public ApiResponse<Map<String, Object>> resumeWorkflow(@PathVariable String workflowId) {
        workflowScheduler.resumeWorkflow(workflowId);
        return ApiResponse.success("Workflow 已恢复执行", Map.of("workflowId", workflowId, "status", "PENDING"));
    }

    /** Sprint 8-E: 重试失败的 Workflow（FAILED → RETRYING，重新入队） */
    @PostMapping("/{workflowId}/retry")
    public ApiResponse<Map<String, Object>> retryWorkflow(@PathVariable String workflowId) {
        workflowScheduler.retryWorkflow(workflowId);
        return ApiResponse.success("Workflow 已重新入队", Map.of("workflowId", workflowId, "status", "RETRYING"));
    }

    /**
     * Sprint 8-E: 查询持久化的 WorkflowInstance（含 status / progress / errorMessage 等）。
     * <p>
     * 优先按 workflowId 查询；不存在时返回错误（区别于 {@link #getWorkflow} 返回 WorkflowResponse）。
     */
    @GetMapping("/{workflowId}/instance")
    public ApiResponse<WorkflowInstance> getWorkflowInstance(@PathVariable String workflowId) {
        return workflowScheduler.getWorkflow(workflowId)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.error("WorkflowInstance 不存在: " + workflowId));
    }

    /** Sprint 8-E: 查询用户持久化的 WorkflowInstance 列表（按创建时间倒序） */
    @GetMapping("/instances")
    public ApiResponse<List<WorkflowInstance>> getUserInstances(@AuthenticationPrincipal User user) {
        return ApiResponse.success(workflowScheduler.getUserWorkflows(user.getId()));
    }
}

