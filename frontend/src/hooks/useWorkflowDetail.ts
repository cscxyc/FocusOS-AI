"use client";

import { useQuery } from "@tanstack/react-query";
import api from "@/lib/api";
import type {
  WorkflowDetailResponse,
  WorkflowInstanceResponse,
  LLMCallLogWithCost,
} from "@/lib/types";

/**
 * Sprint 9-A: Workflow Detail Hooks
 * <p>
 * 获取工作流详情（含任务列表）、持久化实例、关联的 LLM 调用日志。
 */

/** 获取工作流详情（GET /api/workflow/{id}） */
export function useWorkflowDetail(workflowId: string | null) {
  return useQuery({
    queryKey: ["workflow-detail", workflowId],
    queryFn: () => api.get<WorkflowDetailResponse>(`/api/workflow/${workflowId}`),
    enabled: workflowId != null,
    staleTime: 10 * 1000,
  });
}

/** 获取持久化的 WorkflowInstance（GET /api/workflow/{id}/instance） */
export function useWorkflowInstance(workflowId: string | null) {
  return useQuery({
    queryKey: ["workflow-instance", workflowId],
    queryFn: () => api.get<WorkflowInstanceResponse>(`/api/workflow/${workflowId}/instance`),
    enabled: workflowId != null,
    staleTime: 10 * 1000,
    retry: false, // 可能不存在持久化实例
  });
}

/** 获取工作流关联的 LLM 调用日志（GET /api/llm-logs/workflow/{id}） */
export function useWorkflowLLMLogs(workflowId: string | null) {
  return useQuery({
    queryKey: ["workflow-llm-logs", workflowId],
    queryFn: () => api.get<LLMCallLogWithCost[]>(`/api/llm-logs/workflow/${workflowId}`),
    enabled: workflowId != null,
    staleTime: 10 * 1000,
  });
}

/** 获取用户历史工作流列表（GET /api/workflow/history） */
export function useWorkflowHistory() {
  return useQuery({
    queryKey: ["workflow-history"],
    queryFn: () => api.get<WorkflowDetailResponse[]>(`/api/workflow/history`),
    staleTime: 30 * 1000,
  });
}
