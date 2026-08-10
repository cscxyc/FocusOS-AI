"use client";

import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import type { LLMCallLog } from "@/lib/types";

const BASE = "/api/llm-logs";

/** 后端 Map<String,Object> 返回的摘要结构 */
export interface LLMLogSummary {
  totalCalls: number;
  successCalls: number;
  failedCalls: number;
  totalInputTokens: number;
  totalOutputTokens: number;
  totalLatencyMs: number;
  byAgent: Array<{
    agentType: string;
    callCount: number;
    totalTokens: number;
    avgLatencyMs: number;
  }>;
}

/**
 * Sprint 7-C-B: LLM 调用监控 hooks
 */

/** 用户 LLM 调用统计摘要 */
export function useLLMLogSummary() {
  return useQuery<LLMLogSummary>({
    queryKey: ["llm-logs-summary"],
    queryFn: () => api.get<LLMLogSummary>(`${BASE}/summary`),
    staleTime: 30 * 1000,
  });
}

/** 最近 50 条调用记录 */
export function useLLMRecentLogs() {
  return useQuery<LLMCallLog[]>({
    queryKey: ["llm-logs-recent"],
    queryFn: () => api.get<LLMCallLog[]>(`${BASE}/recent`),
    staleTime: 15 * 1000,
  });
}

/** 指定 Workflow 的全部调用 */
export function useLLMWorkflowLogs(workflowId: string | null) {
  return useQuery<LLMCallLog[]>({
    queryKey: ["llm-logs-workflow", workflowId],
    queryFn: () => api.get<LLMCallLog[]>(`${BASE}/workflow/${workflowId}`),
    enabled: workflowId != null,
  });
}
