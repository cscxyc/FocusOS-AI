package com.focusos.controller;

import com.focusos.dto.response.ApiResponse;
import com.focusos.entity.InterviewSession;
import com.focusos.entity.User;
import com.focusos.service.InterviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Sprint 7-B: 模拟面试 Controller
 * <p>
 * 提供：
 * 1. POST /interview/generate-questions — 独立生成面试题 + 创建会话
 * 2. GET  /interview/sessions — 查询用户全部会话
 * 3. GET  /interview/sessions/{sessionId} — 查询指定会话
 * 4. POST /interview/sessions/{sessionId}/answer — 提交回答获取 AI 评价
 * 5. POST /interview/sessions/{sessionId}/complete — 完成会话生成最终评价
 * 6. POST /interview/sessions/{sessionId}/abandon — 放弃会话
 * 7. GET  /interview/by-workflow/{workflowId} — 通过 workflowId 查询会话
 */
@RestController
@RequestMapping("/interview")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;

    /**
     * 独立生成面试题 + 创建 InterviewSession
     * <p>
     * 请求体：{ "jobDescription": "...", "jobTitle": "...", "company": "..." }
     */
    @PostMapping("/generate-questions")
    public ApiResponse<InterviewSession> generateQuestions(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, String> request) {
        String jobDescription = request.get("jobDescription");
        String jobTitle = request.get("jobTitle");
        String company = request.get("company");

        if (jobDescription == null || jobDescription.isBlank()) {
            return ApiResponse.error("职位描述不能为空");
        }

        InterviewSession session = interviewService.generateInterviewQuestions(
                user.getId(), jobDescription, jobTitle, company);
        return ApiResponse.success("面试题生成成功", session);
    }

    /**
     * 查询用户全部面试会话
     */
    @GetMapping("/sessions")
    public ApiResponse<List<InterviewSession>> getSessions(@AuthenticationPrincipal User user) {
        return ApiResponse.success(interviewService.getSessions(user.getId()));
    }

    /**
     * 查询指定会话
     */
    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<InterviewSession> getSession(
            @AuthenticationPrincipal User user,
            @PathVariable Long sessionId) {
        return ApiResponse.success(interviewService.getSession(user.getId(), sessionId));
    }

    /**
     * 提交用户回答，获取 AI 评价
     * <p>
     * 请求体：{ "questionIndex": 0, "userAnswer": "我的回答..." }
     * 返回：{ "session": {...}, "evaluation": "{score, strengths, weaknesses, improvement}" }
     */
    @PostMapping("/sessions/{sessionId}/answer")
    public ApiResponse<Map<String, Object>> submitAnswer(
            @AuthenticationPrincipal User user,
            @PathVariable Long sessionId,
            @RequestBody Map<String, Object> request) {
        Integer questionIndex = (Integer) request.get("questionIndex");
        String userAnswer = (String) request.get("userAnswer");

        if (questionIndex == null) {
            return ApiResponse.error("questionIndex 不能为空");
        }
        if (userAnswer == null || userAnswer.isBlank()) {
            return ApiResponse.error("回答内容不能为空");
        }

        Map<String, Object> result = interviewService.submitAnswer(
                user.getId(), sessionId, questionIndex, userAnswer);
        return ApiResponse.success("回答评价完成", result);
    }

    /**
     * 完成会话，生成最终评价
     * <p>
     * 返回：{ "session": {...}, "finalEvaluation": "{score, strengths, weaknesses, improvement, jobReadiness, focusAreas}" }
     */
    @PostMapping("/sessions/{sessionId}/complete")
    public ApiResponse<Map<String, Object>> completeSession(
            @AuthenticationPrincipal User user,
            @PathVariable Long sessionId) {
        Map<String, Object> result = interviewService.completeSession(user.getId(), sessionId);
        return ApiResponse.success("面试会话已完成", result);
    }

    /**
     * 放弃会话
     */
    @PostMapping("/sessions/{sessionId}/abandon")
    public ApiResponse<Void> abandonSession(
            @AuthenticationPrincipal User user,
            @PathVariable Long sessionId) {
        interviewService.abandonSession(user.getId(), sessionId);
        return ApiResponse.success("会话已放弃", null);
    }

    /**
     * 通过 workflowId 查询会话（Career Workflow 完成后联动查询）
     */
    @GetMapping("/by-workflow/{workflowId}")
    public ApiResponse<InterviewSession> getSessionByWorkflow(@PathVariable String workflowId) {
        InterviewSession session = interviewService.getSessionByWorkflowId(workflowId);
        if (session == null) {
            return ApiResponse.error("未找到该工作流关联的面试会话");
        }
        return ApiResponse.success(session);
    }

    /**
     * Sprint 7-C-B: 修复历史损坏的 InterviewSession.questionsJson
     * <p>
     * 背景：Sprint 7-B 中部分 session 的 questionsJson 因 LLM 输出特殊字符而损坏，
     * 本接口基于 session.jobDescription + Personal RAG 重新生成面试题，
     * 走 LLMJsonSanitizer + DTO 序列化，保证 100% 合法 JSON。
     * <p>
     * 只允许本人 session 修复（所有权校验在 Service 层）。
     */
    @PostMapping("/sessions/{sessionId}/repair")
    public ApiResponse<InterviewSession> repairSession(
            @AuthenticationPrincipal User user,
            @PathVariable Long sessionId) {
        InterviewSession repaired = interviewService.repairSession(user.getId(), sessionId);
        return ApiResponse.success("面试题已修复（基于历史 JD + Personal RAG 重新生成）", repaired);
    }
}
