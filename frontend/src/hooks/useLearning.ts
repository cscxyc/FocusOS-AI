"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import api from "@/lib/api";
import {
  LearningPlan,
  PomodoroSession,
  CreatePlanRequest,
  GeneratePlanRequest,
  GeneratePlanResponse,
  DailyReviewResponse,
} from "@/lib/types";

/** GET /api/learning/plans — fetch all learning plans for the current user. */
export function usePlans() {
  return useQuery({
    queryKey: ["learning-plans"],
    queryFn: () => api.get<LearningPlan[]>("/api/learning/plans"),
    staleTime: 5 * 60 * 1000,
  });
}

/** POST /api/learning/plans — create a new learning plan. */
export function useCreatePlan() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (plan: CreatePlanRequest) =>
      api.post<LearningPlan>("/api/learning/plans", plan),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["learning-plans"] }),
  });
}

/** GET /api/learning/sessions — fetch today's pomodoro sessions. */
export function useSessions() {
  return useQuery({
    queryKey: ["learning-sessions"],
    queryFn: () => api.get<PomodoroSession[]>("/api/learning/sessions"),
    staleTime: 5 * 60 * 1000,
  });
}

/** POST /api/learning/sessions — record a new pomodoro session. */
export function useAddSession() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (session: Omit<PomodoroSession, "id">) =>
      api.post<PomodoroSession>("/api/learning/sessions", session),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["learning-sessions"] });
      queryClient.invalidateQueries({ queryKey: ["dashboard"] });
    },
  });
}

/** POST /api/learning/plans/generate — generate an AI learning plan. */
export function useGeneratePlan() {
  return useMutation({
    mutationFn: (params: GeneratePlanRequest) =>
      api.post<GeneratePlanResponse>("/api/learning/plans/generate", params),
  });
}

/** POST /api/learning/review — run an AI daily review. */
export function useDailyReview() {
  return useMutation({
    mutationFn: () =>
      api.post<DailyReviewResponse>("/api/learning/review"),
  });
}

// ===== Backward-compatible aggregate hook =====
// Kept so existing components (LearningPage, DailyReview) keep working while
// new code migrates to the granular hooks above.

interface LegacyCreatePlanPayload {
  title: string;
  goal?: string;
  startDate?: string;
  endDate?: string;
  dailyTargetMinutes?: number;
  [key: string]: unknown;
}

interface LegacyDailyReviewPayload {
  achievements: string;
  challenges: string;
  mood: "great" | "good" | "neutral" | "bad";
  [key: string]: unknown;
}

export function useLearning() {
  const plansQuery = usePlans();
  const queryClient = useQueryClient();

  const createPlanMutation = useMutation({
    mutationFn: (plan: LegacyCreatePlanPayload) =>
      api.post<LearningPlan>("/api/learning/plans", plan),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["learning-plans"] }),
  });

  const dailyReviewMutation = useMutation({
    mutationFn: (payload: LegacyDailyReviewPayload) =>
      api.post<DailyReviewResponse>("/api/learning/review", payload),
  });

  return {
    plans: plansQuery.data ?? [],
    isLoading: plansQuery.isLoading,
    createPlanMutation,
    dailyReviewMutation,
  };
}
