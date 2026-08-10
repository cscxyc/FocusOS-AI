package com.focusos.controller;

import com.focusos.dto.response.ApiResponse;
import com.focusos.entity.User;
import com.focusos.service.LLMLoggingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Sprint 7-C-B: LLM 调用监控 Controller
 * <p>
 * API：
 * 1. GET /llm-logs/summary              — 用户 LLM 调用统计摘要（Dashboard 展示）
 * 2. GET /llm-logs/recent               — 最近 50 条调用记录
 * 3. GET /llm-logs/workflow/{workflowId} — 指定 Workflow 的全部调用
 */
@RestController
@RequestMapping("/llm-logs")
@RequiredArgsConstructor
public class LLMCallLogController {

    private final LLMLoggingService llmLoggingService;

    /**
     * 用户 LLM 调用统计摘要
     * 返回：totalCalls / successCalls / failedCalls / totalInputTokens / totalOutputTokens / totalLatencyMs / byAgent
     */
    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> getSummary(@AuthenticationPrincipal User user) {
        return ApiResponse.success(llmLoggingService.getSummary(user.getId()));
    }

    /**
     * 最近 50 条 LLM 调用记录
     */
    @GetMapping("/recent")
    public ApiResponse<List<?>> getRecentLogs(@AuthenticationPrincipal User user) {
        return ApiResponse.success(llmLoggingService.getRecentLogs(user.getId()));
    }

    /**
     * 指定 Workflow 的全部 LLM 调用（用于分析单次求职分析的调用链）
     */
    @GetMapping("/workflow/{workflowId}")
    public ApiResponse<List<?>> getWorkflowLogs(
            @AuthenticationPrincipal User user,
            @PathVariable String workflowId) {
        return ApiResponse.success(llmLoggingService.getWorkflowLogs(workflowId));
    }
}
