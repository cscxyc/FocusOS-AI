"use client";

import { useEffect, useRef, useState, useCallback } from "react";
import { getToken } from "@/lib/auth";
import type {
  WorkflowStreamEvent,
  AgentNode,
  AgentNodeStatus,
} from "@/lib/types";

/**
 * Sprint 9-A: SSE Workflow Stream Hook
 * <p>
 * 复用 Sprint 6-B SSE 机制（WorkflowEventBus + EventSource），连接工作流实时事件流，
 * 解析事件并更新 Agent 节点状态。
 * <p>
 * 事件类型：
 * - workflow_started：工作流启动，初始化所有节点为 WAITING
 * - task_started：任务开始，对应节点状态变为 RUNNING
 * - task_completed：任务完成，对应节点状态变为 SUCCESS 或 FAILED
 * - workflow_completed：工作流完成
 * - workflow_failed：工作流失败
 * <p>
 * 使用方式：
 * ```ts
 * const { nodes, subscribe, events, isStreaming, workflowStatus } = useWorkflowStream();
 * subscribe("career-abcd1234");
 * ```
 */

/** Career Workflow 6 步 DAG 节点定义（与后端 AgentWorkflowService 一致） */
const CAREER_WORKFLOW_NODES: AgentNode[] = [
  { id: "career", label: "CareerAgent", agentType: "career", taskType: "CAREER_ANALYSIS", status: "WAITING" },
  { id: "resume", label: "ResumeAgent", agentType: "resume-optimization", taskType: "RESUME_OPTIMIZATION", status: "WAITING" },
  { id: "skill_gap", label: "SkillGapAgent", agentType: "career", taskType: "SKILL_GAP_ANALYSIS", status: "WAITING" },
  { id: "learning", label: "LearningAgent", agentType: "learning", taskType: "LEARNING_PLAN", status: "WAITING" },
  { id: "interview_prep", label: "InterviewAgent", agentType: "interview", taskType: "INTERVIEW_PREPARATION", status: "WAITING" },
  { id: "mock_interview", label: "MockInterview", agentType: "interview", taskType: "MOCK_INTERVIEW", status: "WAITING" },
];

/** DAG 边定义（依赖关系） */
const CAREER_WORKFLOW_EDGES = [
  { from: "career", to: "resume" },
  { from: "career", to: "skill_gap" },
  { from: "career", to: "interview_prep" },
  { from: "skill_gap", to: "learning" },
  { from: "interview_prep", to: "mock_interview" },
];

/** 根据 taskType 匹配节点 ID */
function findNodeIdByTaskType(taskType: string | undefined): string | null {
  if (!taskType) return null;
  const node = CAREER_WORKFLOW_NODES.find((n) => n.taskType === taskType);
  return node ? node.id : null;
}

export function useWorkflowStream() {
  const [nodes, setNodes] = useState<AgentNode[]>(CAREER_WORKFLOW_NODES);
  const [edges] = useState(CAREER_WORKFLOW_EDGES);
  const [events, setEvents] = useState<WorkflowStreamEvent[]>([]);
  const [isStreaming, setIsStreaming] = useState(false);
  const [workflowStatus, setWorkflowStatus] = useState<string>("IDLE");
  const [progress, setProgress] = useState(0);
  const eventSourceRef = useRef<EventSource | null>(null);

  /** 重置所有节点为 WAITING */
  const resetNodes = useCallback(() => {
    setNodes(CAREER_WORKFLOW_NODES.map((n) => ({ ...n, status: "WAITING" as AgentNodeStatus, message: undefined, durationMs: undefined, summary: undefined })));
    setEvents([]);
    setProgress(0);
    setWorkflowStatus("IDLE");
  }, []);

  /** 更新单个节点状态 */
  const updateNode = useCallback((nodeId: string, updates: Partial<AgentNode>) => {
    setNodes((prev) => prev.map((n) => (n.id === nodeId ? { ...n, ...updates } : n)));
  }, []);

  /** 处理 SSE 事件 */
  const handleEvent = useCallback(
    (eventType: string, data: WorkflowStreamEvent) => {
      setEvents((prev) => [...prev, { ...data, event: eventType }]);

      switch (eventType) {
        case "workflow_started":
          setWorkflowStatus("RUNNING");
          resetNodes();
          break;

        case "task_started": {
          const nodeId = findNodeIdByTaskType(data.taskType);
          if (nodeId) {
            updateNode(nodeId, { status: "RUNNING", message: data.task });
          }
          break;
        }

        case "task_completed": {
          const nodeId = findNodeIdByTaskType(data.taskType);
          if (nodeId) {
            const success = data.status !== "FAILED";
            updateNode(nodeId, {
              status: success ? "SUCCESS" : "FAILED",
              durationMs: data.durationMs,
              summary: data.summary,
              message: success ? data.summary : data.errorMessage,
            });
          }
          if (data.completedTasks && data.totalTasks) {
            setProgress(Math.round((data.completedTasks / data.totalTasks) * 100));
          }
          break;
        }

        case "workflow_completed":
          setWorkflowStatus("SUCCESS");
          setProgress(100);
          setIsStreaming(false);
          eventSourceRef.current?.close();
          break;

        case "workflow_failed":
          setWorkflowStatus("FAILED");
          setIsStreaming(false);
          eventSourceRef.current?.close();
          break;
      }
    },
    [resetNodes, updateNode]
  );

  /** 订阅指定 workflowId 的 SSE 事件流 */
  const subscribe = useCallback(
    (workflowId: string) => {
      // 关闭旧连接
      eventSourceRef.current?.close();

      // 重置状态
      resetNodes();
      setIsStreaming(true);
      setWorkflowStatus("RUNNING");

      const token = getToken();
      const baseUrl = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";
      const url = `${baseUrl}/api/workflow/${workflowId}/events?token=${encodeURIComponent(token || "")}`;

      const es = new EventSource(url);
      eventSourceRef.current = es;

      const eventTypes = ["workflow_started", "task_started", "task_completed", "workflow_completed", "workflow_failed"];
      eventTypes.forEach((type) => {
        es.addEventListener(type, (e: MessageEvent) => {
          try {
            const data = JSON.parse(e.data) as WorkflowStreamEvent;
            handleEvent(type, data);
          } catch (err) {
            console.error("SSE 事件解析失败:", err);
          }
        });
      });

      es.onerror = () => {
        // 连接错误时关闭，但不自动改变 workflowStatus（可能还在后端运行）
        es.close();
        setIsStreaming(false);
      };
    },
    [handleEvent, resetNodes]
  );

  /** 获取历史事件（非 SSE，REST 查询） */
  const fetchHistory = useCallback(async (workflowId: string) => {
    try {
      const result = await import("@/lib/api").then((m) => m.default);
      const history = await result.get<{ workflowId: string; events: WorkflowStreamEvent[]; count: number }>(
        `/api/workflow/${workflowId}/events/history`
      );
      if (history.events) {
        setEvents(history.events);
        // 重放历史事件以恢复节点状态
        resetNodes();
        history.events.forEach((evt) => handleEvent(evt.event, evt));
      }
    } catch (err) {
      console.error("获取历史事件失败:", err);
    }
  }, [handleEvent, resetNodes]);

  /** 停止订阅 */
  const unsubscribe = useCallback(() => {
    eventSourceRef.current?.close();
    eventSourceRef.current = null;
    setIsStreaming(false);
  }, []);

  // 组件卸载时关闭 EventSource
  useEffect(() => {
    return () => {
      eventSourceRef.current?.close();
    };
  }, []);

  return {
    nodes,
    edges,
    events,
    isStreaming,
    workflowStatus,
    progress,
    subscribe,
    unsubscribe,
    fetchHistory,
    resetNodes,
  };
}
