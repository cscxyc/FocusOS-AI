"use client";

import * as React from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { useWorkflowStream } from "@/hooks/useWorkflowStream";
import type { AgentNode } from "@/lib/types";
import {
  CheckCircle2,
  XCircle,
  Loader2,
  Circle,
  Zap,
  ArrowDown,
} from "lucide-react";

/** 节点状态样式映射 */
function getNodeStyle(status: string): string {
  switch (status) {
    case "SUCCESS":
      return "border-emerald-500 bg-emerald-50 dark:bg-emerald-950/30";
    case "RUNNING":
      return "border-blue-500 bg-blue-50 dark:bg-blue-950/30 ring-2 ring-blue-400/30";
    case "FAILED":
      return "border-red-500 bg-red-50 dark:bg-red-950/30";
    case "WAITING":
    default:
      return "border-gray-300 bg-gray-50 dark:bg-gray-900/50 dark:border-gray-800";
  }
}

function getNodeIcon(status: string) {
  switch (status) {
    case "SUCCESS":
      return <CheckCircle2 className="h-5 w-5 text-emerald-500" />;
    case "RUNNING":
      return <Loader2 className="h-5 w-5 text-blue-500 animate-spin" />;
    case "FAILED":
      return <XCircle className="h-5 w-5 text-red-500" />;
    case "WAITING":
    default:
      return <Circle className="h-5 w-5 text-gray-300 dark:text-gray-700" />;
  }
}

/** 单个 Agent 节点卡片 */
function AgentNodeCard({ node }: { node: AgentNode }) {
  return (
    <div
      className={`relative rounded-xl border-2 p-3 transition-all duration-300 ${getNodeStyle(node.status)}`}
    >
      <div className="flex items-center gap-2">
        {getNodeIcon(node.status)}
        <div className="flex-1 min-w-0">
          <p className="text-sm font-medium text-gray-900 dark:text-gray-100 truncate">
            {node.label}
          </p>
          <p className="text-xs text-gray-500 dark:text-gray-400 font-mono truncate">
            {node.agentType}
          </p>
        </div>
      </div>
      {node.message && (
        <p className="mt-2 text-xs text-gray-600 dark:text-gray-400 line-clamp-2">
          {node.message}
        </p>
      )}
      {node.durationMs != null && node.status === "SUCCESS" && (
        <p className="mt-1 text-xs text-emerald-600 dark:text-emerald-500 font-mono">
          {(node.durationMs / 1000).toFixed(1)}s
        </p>
      )}
    </div>
  );
}

/** 连接箭头 */
function Arrow() {
  return (
    <div className="flex justify-center py-1">
      <ArrowDown className="h-4 w-4 text-gray-300 dark:text-gray-700" />
    </div>
  );
}

/** 空闲状态（未启动 Workflow） */
function IdleState({ onStart }: { onStart?: () => void }) {
  return (
    <div className="flex flex-col items-center justify-center py-12 text-center">
      <div className="mb-4 rounded-full bg-gradient-to-r from-brand-500/10 to-accent-500/10 p-4">
        <Zap className="h-8 w-8 text-brand-500" />
      </div>
      <h3 className="text-sm font-medium text-gray-900 dark:text-gray-100">
        Agent 执行可视化
      </h3>
      <p className="mt-1 text-sm text-gray-500 dark:text-gray-400 max-w-sm">
        启动 Career Workflow 后，这里将实时展示 6 个 Agent 的执行过程
      </p>
      {onStart && (
        <Button onClick={onStart} className="mt-4" size="sm">
          <Zap className="mr-1.5 h-4 w-4" />
          启动 Career Workflow
        </Button>
      )}
    </div>
  );
}

interface AgentExecutionGraphProps {
  /** 工作流 ID（传入后自动订阅 SSE） */
  workflowId?: string | null;
  /** 空闲状态下"启动"按钮回调 */
  onStart?: () => void;
  /** 是否显示标题栏 */
  showHeader?: boolean;
}

export function AgentExecutionGraph({
  workflowId,
  onStart,
  showHeader = true,
}: AgentExecutionGraphProps) {
  const {
    nodes,
    edges,
    isStreaming,
    workflowStatus,
    progress,
    subscribe,
    fetchHistory,
  } = useWorkflowStream();

  // 当 workflowId 变化时，订阅 SSE 或获取历史
  React.useEffect(() => {
    // eslint-disable-next-line react-hooks/exhaustive-deps
    if (!workflowId) return;

    // 先尝试获取历史（判断是否已完成的工作流）
    fetchHistory(workflowId).then(() => {
      // 如果状态不是终态，订阅 SSE
      // fetchHistory 内部会重放历史事件更新状态
    });

    // 订阅实时事件
    subscribe(workflowId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [workflowId]);

  const hasWorkflow = !!workflowId;

  const content = !hasWorkflow ? (
    <IdleState onStart={onStart} />
  ) : (
    <div className="space-y-2">
      {/* 进度条 */}
      {(isStreaming || workflowStatus === "SUCCESS" || workflowStatus === "FAILED") && (
        <div className="mb-4">
          <div className="flex items-center justify-between mb-1.5">
            <span className="text-xs font-medium text-gray-600 dark:text-gray-400">
              {workflowStatus === "RUNNING" && "工作流执行中..."}
              {workflowStatus === "SUCCESS" && "工作流已完成"}
              {workflowStatus === "FAILED" && "工作流执行失败"}
              {workflowStatus === "IDLE" && "等待启动..."}
            </span>
            <span className="text-xs font-mono text-gray-500">{progress}%</span>
          </div>
          <div className="h-1.5 w-full rounded-full bg-gray-100 dark:bg-gray-900 overflow-hidden">
            <div
              className="h-full rounded-full bg-gradient-to-r from-brand-500 to-accent-500 transition-all duration-500"
              style={{ width: `${progress}%` }}
            />
          </div>
        </div>
      )}

      {/* DAG 图：career → resume / skill_gap / interview_prep → learning / mock_interview */}
      {/* 第 1 层：career */}
      <AgentNodeCard node={nodes[0]} />

      {/* 第 1→2 层连接 */}
      <Arrow />

      {/* 第 2 层：resume, skill_gap, interview_prep（并行） */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-2">
        <AgentNodeCard node={nodes[1]} />
        <AgentNodeCard node={nodes[2]} />
        <AgentNodeCard node={nodes[3]} />
      </div>

      {/* 第 2→3 层连接 */}
      <Arrow />

      {/* 第 3 层：learning, mock_interview */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
        <AgentNodeCard node={nodes[4]} />
        <AgentNodeCard node={nodes[5]} />
      </div>
    </div>
  );

  if (!showHeader) {
    return <div className="p-4">{content}</div>;
  }

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between">
          <CardTitle className="text-lg">Agent Execution Graph</CardTitle>
          {hasWorkflow && (
            <Badge
              variant={
                workflowStatus === "SUCCESS"
                  ? "success"
                  : workflowStatus === "FAILED"
                  ? "destructive"
                  : workflowStatus === "RUNNING"
                  ? "default"
                  : "secondary"
              }
            >
              {workflowStatus === "RUNNING" && "执行中"}
              {workflowStatus === "SUCCESS" && "已完成"}
              {workflowStatus === "FAILED" && "失败"}
              {workflowStatus === "IDLE" && "等待中"}
            </Badge>
          )}
        </div>
      </CardHeader>
      <CardContent>{content}</CardContent>
    </Card>
  );
}
