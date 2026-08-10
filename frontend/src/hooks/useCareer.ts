"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import api from "@/lib/api";
import {
  CareerProfile,
  JDAnalysisRequest,
  JDAnalysisResponse,
  ResumeOptimizeRequest,
  ResumeOptimizeResponse,
  JobApplication,
  CareerGrowthPlan,
  GenerateCareerGrowthRequest,
  GenerateCareerGrowthResponse,
} from "@/lib/types";

/** GET /api/career/profile — fetch the current user's career profile. */
export function useProfile() {
  return useQuery({
    queryKey: ["career-profile"],
    queryFn: () => api.get<CareerProfile>("/api/career/profile"),
    staleTime: 5 * 60 * 1000,
  });
}

/** POST /api/career/jd-analyze — analyze a job description against the resume. */
export function useAnalyzeJD() {
  return useMutation({
    mutationFn: (data: JDAnalysisRequest) =>
      api.post<JDAnalysisResponse>("/api/career/jd-analyze", data),
  });
}

/** POST /api/career/resume-optimize — optimize the resume for a job description. */
export function useOptimizeResume() {
  return useMutation({
    mutationFn: (data: ResumeOptimizeRequest) =>
      api.post<ResumeOptimizeResponse>("/api/career/resume-optimize", data),
  });
}

/** GET /api/career/applications — fetch all job applications. */
export function useApplications() {
  return useQuery({
    queryKey: ["career-applications"],
    queryFn: () => api.get<JobApplication[]>("/api/career/applications"),
    staleTime: 5 * 60 * 1000,
  });
}

// ===== Backward-compatible aggregate hook =====
// Kept so existing components (JDAnalyzer, ResumeOptimizer) keep working while
// new code migrates to the granular hooks above. The legacy mutations accept a
// raw JD string (as the components pass) and wrap it into the request body.

export function useCareer() {
  const queryClient = useQueryClient();

  const profileQuery = useProfile();
  const applicationsQuery = useApplications();

  const createProfileMutation = useMutation({
    mutationFn: (profileData: Record<string, unknown>) =>
      api.post<CareerProfile>("/api/career/profile", profileData),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["career-profile"] }),
  });

  const analyzeJDMutation = useMutation({
    mutationFn: (jdText: string) =>
      api.post<JDAnalysisResponse>("/api/career/jd-analyze", {
        jobDescription: jdText,
      } satisfies JDAnalysisRequest),
  });

  const optimizeResumeMutation = useMutation({
    mutationFn: (jobDescription: string) =>
      api.post<ResumeOptimizeResponse>("/api/career/resume-optimize", {
        jobDescription,
      } satisfies ResumeOptimizeRequest),
  });

  return {
    profile: profileQuery.data,
    applications: applicationsQuery.data ?? [],
    isLoading: profileQuery.isLoading,
    createProfile: createProfileMutation.mutate,
    analyzeJDMutation,
    optimizeResumeMutation,
    isAnalyzing: analyzeJDMutation.isPending,
    isOptimizing: optimizeResumeMutation.isPending,
  };
}

// ============ Sprint 8-B: Career Growth ============

const CAREER_BASE = "/api/career";

/**
 * 生成职业成长规划
 * POST /api/career/growth
 */
export function useGenerateCareerGrowth() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: GenerateCareerGrowthRequest) =>
      api.post<GenerateCareerGrowthResponse>(`${CAREER_BASE}/growth`, data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["career-growth-plans"] });
    },
  });
}

/**
 * 获取指定成长规划（含完整明细）
 * GET /api/career/growth/{planId}
 */
export function useCareerGrowthPlan(planId: number | null) {
  return useQuery<GenerateCareerGrowthResponse>({
    queryKey: ["career-growth-plan", planId],
    queryFn: () => api.get<GenerateCareerGrowthResponse>(`${CAREER_BASE}/growth/${planId}`),
    enabled: planId != null,
  });
}

/**
 * 查询用户全部成长规划历史
 * GET /api/career/growth
 */
export function useCareerGrowthPlans() {
  return useQuery<CareerGrowthPlan[]>({
    queryKey: ["career-growth-plans"],
    queryFn: () => api.get<CareerGrowthPlan[]>(`${CAREER_BASE}/growth`),
    staleTime: 30 * 1000,
  });
}

/**
 * 查询指定简历版本的成长规划历史
 * GET /api/career/versions/{versionId}/growth
 */
export function useResumeVersionGrowthPlans(versionId: number | null) {
  return useQuery<CareerGrowthPlan[]>({
    queryKey: ["resume-version-growth-plans", versionId],
    queryFn: () =>
      api.get<CareerGrowthPlan[]>(`${CAREER_BASE}/versions/${versionId}/growth`),
    enabled: versionId != null,
  });
}
