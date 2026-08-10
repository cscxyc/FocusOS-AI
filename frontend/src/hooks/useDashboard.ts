"use client";

import { useQuery } from "@tanstack/react-query";
import api from "@/lib/api";
import { DashboardResponse, AIRecommendation } from "@/lib/types";

/**
 * Fetches the aggregated dashboard data (GET /api/dashboard) and the AI
 * recommendation (GET /api/dashboard/ai-recommendation) in parallel.
 * Returns both responses fully typed.
 */
export function useDashboard() {
  const dashboardQuery = useQuery({
    queryKey: ["dashboard"],
    queryFn: () => api.get<DashboardResponse>("/api/dashboard"),
    staleTime: 60 * 1000,
  });

  const aiRecommendationQuery = useQuery({
    queryKey: ["dashboard-ai-recommendation"],
    queryFn: () => api.get<AIRecommendation>("/api/dashboard/ai-recommendation"),
    staleTime: 60 * 1000,
  });

  return {
    data: dashboardQuery.data,
    aiRecommendation: aiRecommendationQuery.data,
    isLoading: dashboardQuery.isLoading,
    isError: dashboardQuery.isError,
    error: dashboardQuery.error,
    refetch: dashboardQuery.refetch,
    isRecommendationLoading: aiRecommendationQuery.isLoading,
  };
}
