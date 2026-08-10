"use client";

import { useQuery } from "@tanstack/react-query";
import api from "@/lib/api";
import type { DashboardOverview, TimelineResponse } from "@/lib/types";

/**
 * Sprint 9-A: Dashboard Overview Hook
 * <p>
 * 获取用户 AI 职业状态聚合数据（GET /api/dashboard/overview）。
 * 包含简历评分、JD 匹配最高分、模拟面试成绩、学习成长进度、Memory 数量、最近活动列表。
 */
export function useDashboardOverview() {
  return useQuery({
    queryKey: ["dashboard-overview"],
    queryFn: () => api.get<DashboardOverview>("/api/dashboard/overview"),
    staleTime: 60 * 1000,
  });
}

/**
 * Sprint 9-A: Career Timeline Hook
 * <p>
 * 获取用户求职流程时间线（GET /api/dashboard/timeline）。
 * 返回 6 个有序阶段（对应 Career Workflow 的 6 步 DAG）。
 */
export function useCareerTimeline() {
  return useQuery({
    queryKey: ["career-timeline"],
    queryFn: () => api.get<TimelineResponse>("/api/dashboard/timeline"),
    staleTime: 60 * 1000,
  });
}
