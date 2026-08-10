"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { getToken } from "@/lib/auth";
import type {
  ResumeVersion,
  CreateResumeVersionRequest,
  UpdateResumeVersionRequest,
  ResumeDiffResponse,
  ExportFormat,
  EvaluateResumeRequest,
  EvaluateResumeResponse,
  ResumeEvaluationReport,
} from "@/lib/types";

const BASE = "/api/resume";

/**
 * Sprint 7-C-B: Resume Workspace hooks
 */
export function useResumeVersions() {
  return useQuery<ResumeVersion[]>({
    queryKey: ["resume-versions"],
    queryFn: () => api.get<ResumeVersion[]>(`${BASE}/versions`),
    staleTime: 30 * 1000,
  });
}

export function useResumeVersion(versionId: number | null) {
  return useQuery<ResumeVersion>({
    queryKey: ["resume-version", versionId],
    queryFn: () => api.get<ResumeVersion>(`${BASE}/versions/${versionId}`),
    enabled: versionId != null,
  });
}

export function useActiveResumeVersion() {
  return useQuery<ResumeVersion | null>({
    queryKey: ["resume-active"],
    queryFn: () => api.get<ResumeVersion | null>(`${BASE}/active`),
    staleTime: 30 * 1000,
  });
}

export function useCreateResumeVersion() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateResumeVersionRequest) =>
      api.post<ResumeVersion>(`${BASE}/versions`, data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["resume-versions"] });
      qc.invalidateQueries({ queryKey: ["resume-active"] });
    },
  });
}

export function useUpdateResumeVersion() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      versionId,
      data,
    }: {
      versionId: number;
      data: UpdateResumeVersionRequest;
    }) => api.put<ResumeVersion>(`${BASE}/versions/${versionId}`, data),
    onSuccess: (data) => {
      qc.invalidateQueries({ queryKey: ["resume-versions"] });
      qc.invalidateQueries({ queryKey: ["resume-version", data.id] });
    },
  });
}

export function useDeleteResumeVersion() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (versionId: number) =>
      api.delete<void>(`${BASE}/versions/${versionId}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["resume-versions"] });
      qc.invalidateQueries({ queryKey: ["resume-active"] });
    },
  });
}

export function useActivateResumeVersion() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (versionId: number) =>
      api.post<ResumeVersion>(`${BASE}/versions/${versionId}/activate`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["resume-versions"] });
      qc.invalidateQueries({ queryKey: ["resume-active"] });
    },
  });
}

export function useResumeDiff() {
  return useMutation({
    mutationFn: ({ versionA, versionB }: { versionA: number; versionB: number }) =>
      api.get<ResumeDiffResponse>(
        `${BASE}/diff?versionA=${versionA}&versionB=${versionB}`
      ),
  });
}

/**
 * 下载简历导出文件（不走 axios 拦截器，直接 fetch 二进制）
 * 因为 api.ts 拦截器会解包 {code, data} 信封，二进制响应不适用
 */
export async function downloadResumeExport(
  versionId: number,
  format: ExportFormat
): Promise<{ blob: Blob; filename: string }> {
  const token = getToken();
  const url = `${BASE}/versions/${versionId}/export?format=${format}`;
  const resp = await fetch(
    (process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080") + url,
    {
      method: "GET",
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    }
  );
  if (!resp.ok) {
    throw new Error(`导出失败: ${resp.status} ${resp.statusText}`);
  }
  const blob = await resp.blob();

  // 从 Content-Disposition 解析文件名
  const cd = resp.headers.get("Content-Disposition") || "";
  let filename = `resume-${versionId}.${format}`;
  const match = cd.match(/filename\*?=(?:UTF-8'')?([^;]+)/i);
  if (match) {
    try {
      filename = decodeURIComponent(match[1].replace(/^"|"$/g, ""));
    } catch {
      filename = match[1].replace(/^"|"$/g, "");
    }
  }
  return { blob, filename };
}

// ============ Sprint 8-A: Resume Evaluation ============

/**
 * 执行简历 AI 评分
 * POST /api/resume/evaluate
 */
export function useEvaluateResume() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: EvaluateResumeRequest) =>
      api.post<EvaluateResumeResponse>(`${BASE}/evaluate`, data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["resume-evaluations"] });
    },
  });
}

/**
 * 获取指定评估结果（含完整评分明细）
 * GET /api/resume/evaluations/{evaluationId}
 */
export function useResumeEvaluation(evaluationId: number | null) {
  return useQuery<EvaluateResumeResponse>({
    queryKey: ["resume-evaluation", evaluationId],
    queryFn: () => api.get<EvaluateResumeResponse>(`${BASE}/evaluations/${evaluationId}`),
    enabled: evaluationId != null,
  });
}

/**
 * 查询用户全部评估历史
 * GET /api/resume/evaluations
 */
export function useResumeEvaluations() {
  return useQuery<ResumeEvaluationReport[]>({
    queryKey: ["resume-evaluations"],
    queryFn: () => api.get<ResumeEvaluationReport[]>(`${BASE}/evaluations`),
    staleTime: 30 * 1000,
  });
}

/**
 * 查询指定简历版本的评估历史
 * GET /api/resume/versions/{versionId}/evaluations
 */
export function useResumeVersionEvaluations(versionId: number | null) {
  return useQuery<ResumeEvaluationReport[]>({
    queryKey: ["resume-version-evaluations", versionId],
    queryFn: () => api.get<ResumeEvaluationReport[]>(`${BASE}/versions/${versionId}/evaluations`),
    enabled: versionId != null,
  });
}
