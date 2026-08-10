package com.focusos.controller;

import com.focusos.dto.request.CreateLearningPlanRequest;
import com.focusos.dto.request.CreateLearningSessionRequest;
import com.focusos.dto.response.ApiResponse;
import com.focusos.dto.response.LearningPlanResponse;
import com.focusos.dto.response.LearningSessionResponse;
import com.focusos.entity.User;
import com.focusos.service.LearningService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/learning")
@RequiredArgsConstructor
public class LearningController {

    private final LearningService learningService;

    @PostMapping("/plans")
    public ApiResponse<LearningPlanResponse> createPlan(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateLearningPlanRequest request) {
        return ApiResponse.success("学习计划创建成功", learningService.createPlan(user.getId(), request));
    }

    @GetMapping("/plans")
    public ApiResponse<List<LearningPlanResponse>> getPlans(@AuthenticationPrincipal User user) {
        return ApiResponse.success(learningService.getPlans(user.getId()));
    }

    @GetMapping("/plans/{status}")
    public ApiResponse<List<LearningPlanResponse>> getPlansByStatus(
            @AuthenticationPrincipal User user,
            @PathVariable String status) {
        return ApiResponse.success(learningService.getPlansByStatus(user.getId(), status));
    }

    @PostMapping("/sessions")
    public ApiResponse<LearningSessionResponse> addSession(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateLearningSessionRequest request) {
        return ApiResponse.success("学习记录添加成功", learningService.addSession(user.getId(), request));
    }

    @GetMapping("/sessions")
    public ApiResponse<List<LearningSessionResponse>> getSessions(@AuthenticationPrincipal User user) {
        return ApiResponse.success(learningService.getSessions(user.getId()));
    }

    @GetMapping("/sessions/today")
    public ApiResponse<List<LearningSessionResponse>> getTodaySessions(@AuthenticationPrincipal User user) {
        return ApiResponse.success(learningService.getTodaySessions(user.getId()));
    }

    @GetMapping("/sessions/plan/{planId}")
    public ApiResponse<List<LearningSessionResponse>> getSessionsByPlan(
            @AuthenticationPrincipal User user,
            @PathVariable Long planId) {
        return ApiResponse.success(learningService.getSessionsByPlan(user.getId(), planId));
    }

    @PostMapping("/plans/generate")
    public ApiResponse<String> generatePlanWithAI(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, Object> request) {
        String goal = (String) request.get("goal");
        int durationWeeks = request.containsKey("durationWeeks") ?
                ((Number) request.get("durationWeeks")).intValue() : 4;
        int dailyMinutes = request.containsKey("dailyMinutes") ?
                ((Number) request.get("dailyMinutes")).intValue() : 60;
        return ApiResponse.success("AI学习计划生成成功",
                learningService.generatePlanWithAI(user.getId(), goal, durationWeeks, dailyMinutes));
    }

    @PostMapping("/review")
    public ApiResponse<String> dailyReview(@AuthenticationPrincipal User user) {
        return ApiResponse.success("每日复盘生成成功", learningService.dailyReviewWithAI(user.getId()));
    }

    @GetMapping("/stats/today")
    public ApiResponse<Map<String, Object>> getTodayStats(@AuthenticationPrincipal User user) {
        Map<String, Object> stats = Map.of(
                "todayMinutes", learningService.getTodayTotalMinutes(user.getId()),
                "weeklyAvgFocus", learningService.getWeeklyAvgFocus(user.getId())
        );
        return ApiResponse.success(stats);
    }
}
