package com.focusos.controller;

import com.focusos.dto.response.ApiResponse;
import com.focusos.dto.response.DashboardOverviewResponse;
import com.focusos.dto.response.DashboardResponse;
import com.focusos.dto.response.TimelineResponse;
import com.focusos.entity.User;
import com.focusos.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public ApiResponse<DashboardResponse> getDashboard(@AuthenticationPrincipal User user) {
        return ApiResponse.success(dashboardService.getDashboardData(user.getId()));
    }

    @GetMapping("/ai-recommendation")
    public ApiResponse<String> getAIRecommendation(@AuthenticationPrincipal User user) {
        return ApiResponse.success("AI建议生成成功", dashboardService.getAIRecommendation(user.getId()));
    }

    /**
     * Sprint 6-A: AI 计划面板
     * 展示今日AI建议、本周目标、学习任务、职业进度、AI工作流任务
     */
    @GetMapping("/ai-plan")
    public ApiResponse<Map<String, Object>> getAIPlan(@AuthenticationPrincipal User user) {
        return ApiResponse.success("AI计划获取成功", dashboardService.getAIPlan(user.getId()));
    }

    /**
     * Sprint 9-A: Dashboard 首页聚合数据
     * <p>
     * 返回用户当前 AI 职业状态：简历评分、JD 匹配最高分、模拟面试成绩、
     * 学习成长进度、Memory 数量、最近活动列表。
     */
    @GetMapping("/overview")
    public ApiResponse<DashboardOverviewResponse> getOverview(@AuthenticationPrincipal User user) {
        return ApiResponse.success(dashboardService.getOverview(user.getId()));
    }

    /**
     * Sprint 9-A: Career Journey Timeline
     * <p>
     * 将用户求职流程可视化为 6 个有序阶段（对应 Career Workflow 的 6 步 DAG）。
     */
    @GetMapping("/timeline")
    public ApiResponse<TimelineResponse> getTimeline(@AuthenticationPrincipal User user) {
        return ApiResponse.success(dashboardService.getTimeline(user.getId()));
    }
}
