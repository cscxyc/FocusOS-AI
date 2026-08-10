"use client";

import * as React from "react";
import { useParams, useRouter } from "next/navigation";
import { Sidebar } from "@/components/layout/Sidebar";
import { Header } from "@/components/layout/Header";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { AgentExecutionGraph } from "@/components/workflow/AgentExecutionGraph";
import {
  useWorkflowDetail,
  useWorkflowInstance,
  useWorkflowLLMLogs,
} from "@/hooks/useWorkflowDetail";
import type { WorkflowTaskResponse, LLMCallLogWithCost } from "@/lib/types";
import {
  ArrowLeft,
  Clock,
  CheckCircle2,
  XCircle,
  Loader2,
  Circle,
  Cpu,
  Coins,
  Zap,
  AlertCircle,
} from "lucide-react";

const TASK_LABELS: Record<string, string> = {
  CAREER_ANALYSIS: "岗位匹配分析",
  RESUME_OPTIMIZATION: "简历优化（STAR）",
  SKILL_GAP_ANALYSIS: "技能差距分析",
  LEARNING_PLAN: "学习计划生成",
  INTERVIEW_PREPARATION: "面试题生成",
  MOCK_INTERVIEW: "模拟面试初始化",
  CONTEXT_INIT: "检索个人知识库",
};

function getStatusIcon(status: string) {
  switch (status) {
    case "SUCCESS":
      return <CheckCircle2 className="h-4 w-4 text-emerald-500" />;
    case "FAILED":
      return <XCircle className="h-4 w-4 text-red-500" />;
    case "RUNNING":
      return <Loader2 className="h-4 w-4 text-blue-500 animate-spin" />;
    case "PENDING":
      return <Circle className="h-4 w-4 text-amber-400" />;
    default:
      return <Circle className="h-4 w-4 text-gray-300" />;
  }
}

function formatDuration(ms?: number | null): string {
  if (ms == null) return "—";
  if (ms < 1000) return `${ms}ms`;
  return `${(ms / 1000).toFixed(1)}s`;
}

function formatCost(cost?: number | null): string {
  if (cost == null) return "—";
  if (cost < 0.01) return `$${cost.toFixed(4)}`;
  return `$${cost.toFixed(2)}`;
}

function formatTokens(tokens?: number | null): string {
  if (tokens == null) return "—";
  if (tokens >= 1000) return `${(tokens / 1000).toFixed(1)}k`;
  return `${tokens}`;
}

/** 工作流概览卡片 */
function WorkflowOverview({
  workflowId,
  detail,
  instance,
}: {
  workflowId: string;
  detail?: { userGoal: string; status: string; totalTasks: number; successTasks: number; failedTasks: number; createdAt: string };
  instance?: { status: string; progress?: number | null; startedAt?: string | null; completedAt?: string | null; currentTask?: string | null };
}) {
  const status = instance?.status || detail?.status || "UNKNOWN";
  const totalDuration = instance?.startedAt && instance?.completedAt
    ? new Date(instance.completedAt).getTime() - new Date(instance.startedAt).getTime()
    : null;

  return (
    <Card>
      <CardContent className="p-5">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2 mb-2">
              <h2 className="text-lg font-semibold text-gray-900 dark:text-gray-100 truncate">
                {detail?.userGoal || "工作流详情"}
              </h2>
              <Badge
                variant={
                  status === "SUCCESS" ? "success" :
                  status === "FAILED" ? "destructive" :
                  status === "RUNNING" || status === "RETRYING" ? "default" :
                  status === "PENDING" || status === "PAUSED" ? "warning" :
                  "secondary"
                }
              >
                {status}
              </Badge>
            </div>
            <p className="text-xs text-gray-400 dark:text-gray-600 font-mono mb-3">
              {workflowId}
            </p>
            <div className="flex flex-wrap gap-4 text-xs">
              <div className="flex items-center gap-1.5">
                <Clock className="h-3.5 w-3.5 text-gray-400" />
                <span className="text-gray-500 dark:text-gray-400">创建：</span>
                <span className="text-gray-700 dark:text-gray-300 font-mono">
                  {detail?.createdAt ? new Date(detail.createdAt).toLocaleString("zh-CN") : "—"}
                </span>
              </div>
              {totalDuration != null && (
                <div className="flex items-center gap-1.5">
                  <Zap className="h-3.5 w-3.5 text-gray-400" />
                  <span className="text-gray-500 dark:text-gray-400">耗时：</span>
                  <span className="text-gray-700 dark:text-gray-300 font-mono">
                    {formatDuration(totalDuration)}
                  </span>
                </div>
              )}
              {instance?.progress != null && (
                <div className="flex items-center gap-1.5">
                  <span className="text-gray-500 dark:text-gray-400">进度：</span>
                  <span className="text-gray-700 dark:text-gray-300 font-mono">{instance.progress}%</span>
                </div>
              )}
            </div>
          </div>
          <div className="flex gap-2">
            {detail && (
              <div className="text-right">
                <div className="flex gap-3">
                  <div>
                    <p className="text-xs text-gray-400">总任务</p>
                    <p className="text-lg font-bold text-gray-900 dark:text-gray-100">{detail.totalTasks}</p>
                  </div>
                  <div>
                    <p className="text-xs text-emerald-500">成功</p>
                    <p className="text-lg font-bold text-emerald-600 dark:text-emerald-400">{detail.successTasks}</p>
                  </div>
                  <div>
                    <p className="text-xs text-red-500">失败</p>
                    <p className="text-lg font-bold text-red-600 dark:text-red-400">{detail.failedTasks}</p>
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>
        {instance?.currentTask && (
          <div className="mt-3 pt-3 border-t border-gray-100 dark:border-gray-900">
            <p className="text-xs text-gray-500 dark:text-gray-400">
              当前任务：<span className="font-medium text-gray-700 dark:text-gray-300">{instance.currentTask}</span>
            </p>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

/** 任务列表项 */
function TaskListItem({ task, llmLog }: { task: WorkflowTaskResponse; llmLog?: LLMCallLogWithCost }) {
  const [expanded, setExpanded] = React.useState(false);
  const label = TASK_LABELS[task.taskType] || task.taskType;

  return (
    <div className="rounded-lg border border-gray-100 dark:border-gray-900 overflow-hidden">
      <button
        onClick={() => setExpanded(!expanded)}
        className="w-full flex items-center gap-3 p-3 hover:bg-gray-50 dark:hover:bg-gray-900/50 transition-colors text-left"
      >
        {getStatusIcon(task.status)}
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <span className="text-sm font-medium text-gray-900 dark:text-gray-100">{label}</span>
            <Badge variant="outline" className="text-xs font-mono">{task.taskType}</Badge>
          </div>
          <p className="text-xs text-gray-400 dark:text-gray-600 font-mono">{task.agentType}</p>
        </div>
        {task.durationMs != null && (
          <span className="text-xs text-gray-500 dark:text-gray-400 font-mono">
            {formatDuration(task.durationMs)}
          </span>
        )}
      </button>

      {expanded && (
        <div className="px-3 pb-3 pt-1 border-t border-gray-50 dark:border-gray-950 space-y-2">
          {/* LLM 调用信息 */}
          {llmLog && (
            <div className="grid grid-cols-2 md:grid-cols-4 gap-2 text-xs">
              <div className="flex items-center gap-1.5">
                <Cpu className="h-3.5 w-3.5 text-gray-400" />
                <span className="text-gray-500">模型：</span>
                <span className="text-gray-700 dark:text-gray-300 font-mono">{llmLog.model}</span>
              </div>
              <div className="flex items-center gap-1.5">
                <Zap className="h-3.5 w-3.5 text-gray-400" />
                <span className="text-gray-500">Token：</span>
                <span className="text-gray-700 dark:text-gray-300 font-mono">
                  {formatTokens(llmLog.inputTokens)} + {formatTokens(llmLog.outputTokens)}
                </span>
              </div>
              {llmLog.estimatedCost != null && (
                <div className="flex items-center gap-1.5">
                  <Coins className="h-3.5 w-3.5 text-gray-400" />
                  <span className="text-gray-500">成本：</span>
                  <span className="text-gray-700 dark:text-gray-300 font-mono">{formatCost(llmLog.estimatedCost)}</span>
                </div>
              )}
              {llmLog.latencyMs != null && (
                <div className="flex items-center gap-1.5">
                  <Clock className="h-3.5 w-3.5 text-gray-400" />
                  <span className="text-gray-500">延迟：</span>
                  <span className="text-gray-700 dark:text-gray-300 font-mono">{formatDuration(llmLog.latencyMs)}</span>
                </div>
              )}
            </div>
          )}

          {/* 任务结果 */}
          {task.result && (
            <div>
              <p className="text-xs text-gray-500 dark:text-gray-400 mb-1">结果：</p>
              <pre className="text-xs text-gray-700 dark:text-gray-300 bg-gray-50 dark:bg-gray-950 rounded p-2 max-h-48 overflow-auto whitespace-pre-wrap break-words">
                {task.result.length > 500 ? task.result.slice(0, 500) + "..." : task.result}
              </pre>
            </div>
          )}

          {/* 错误信息 */}
          {task.errorMessage && (
            <div>
              <p className="text-xs text-red-500 mb-1">错误：</p>
              <pre className="text-xs text-red-600 dark:text-red-400 bg-red-50 dark:bg-red-950/30 rounded p-2 whitespace-pre-wrap break-words">
                {task.errorMessage}
              </pre>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

export default function WorkflowDetailPage() {
  const params = useParams();
  const router = useRouter();
  const workflowId = (params?.id as string) || null;

  const { data: detail, isLoading: detailLoading, isError: detailError } = useWorkflowDetail(workflowId);
  const { data: instance } = useWorkflowInstance(workflowId);
  const { data: llmLogs } = useWorkflowLLMLogs(workflowId);

  // 按 taskType 索引 LLM 日志
  const llmLogByAgentType = React.useMemo(() => {
    const map = new Map<string, LLMCallLogWithCost>();
    if (llmLogs) {
      llmLogs.forEach((log) => {
        if (log.agentType) map.set(log.agentType, log);
      });
    }
    return map;
  }, [llmLogs]);

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-950">
      <Sidebar />
      <div className="lg:pl-64">
        <Header />
        <main className="p-6 space-y-6">
          {/* 返回按钮 */}
          <button
            onClick={() => router.back()}
            className="inline-flex items-center gap-1.5 text-sm text-gray-500 hover:text-gray-700 dark:hover:text-gray-300 transition-colors"
          >
            <ArrowLeft className="h-4 w-4" />
            返回
          </button>

          <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">工作流详情</h1>

          {detailLoading ? (
            <Card>
              <CardContent className="p-8 flex items-center justify-center">
                <Loader2 className="h-6 w-6 text-brand-500 animate-spin" />
                <span className="ml-2 text-sm text-gray-500">加载工作流详情...</span>
              </CardContent>
            </Card>
          ) : detailError ? (
            <Card>
              <CardContent className="p-8">
                <div className="flex flex-col items-center text-center">
                  <AlertCircle className="mb-2 h-8 w-8 text-red-400" />
                  <p className="text-sm text-gray-500 mb-2">工作流不存在或加载失败</p>
                  <p className="text-xs text-gray-400 font-mono mb-3">{workflowId}</p>
                  <Button onClick={() => router.push("/dashboard")} variant="outline" size="sm">
                    返回 Dashboard
                  </Button>
                </div>
              </CardContent>
            </Card>
          ) : (
            <>
              {/* 工作流概览 */}
              <WorkflowOverview
                workflowId={workflowId || ""}
                detail={detail}
                instance={instance}
              />

              {/* Agent 执行图 */}
              <AgentExecutionGraph workflowId={workflowId} />

              {/* 任务列表 */}
              <Card>
                <CardHeader>
                  <CardTitle className="text-lg">任务列表</CardTitle>
                </CardHeader>
                <CardContent className="space-y-2">
                  {detail?.tasks && detail.tasks.length > 0 ? (
                    detail.tasks.map((task) => (
                      <TaskListItem
                        key={task.id}
                        task={task}
                        llmLog={llmLogByAgentType.get(task.agentType)}
                      />
                    ))
                  ) : (
                    <div className="py-8 text-center">
                      <Circle className="mx-auto mb-2 h-8 w-8 text-gray-300" />
                      <p className="text-sm text-gray-500">暂无任务数据</p>
                    </div>
                  )}
                </CardContent>
              </Card>

              {/* LLM 调用汇总 */}
              {llmLogs && llmLogs.length > 0 && (
                <Card>
                  <CardHeader>
                    <CardTitle className="text-lg">LLM 调用汇总</CardTitle>
                  </CardHeader>
                  <CardContent>
                    <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                      <div className="text-center">
                        <p className="text-xs text-gray-500 mb-1">总调用数</p>
                        <p className="text-xl font-bold text-gray-900 dark:text-gray-100">{llmLogs.length}</p>
                      </div>
                      <div className="text-center">
                        <p className="text-xs text-gray-500 mb-1">总 Token</p>
                        <p className="text-xl font-bold text-gray-900 dark:text-gray-100">
                          {formatTokens(llmLogs.reduce((sum, log) => sum + (log.inputTokens || 0) + (log.outputTokens || 0), 0))}
                        </p>
                      </div>
                      <div className="text-center">
                        <p className="text-xs text-gray-500 mb-1">总成本</p>
                        <p className="text-xl font-bold text-gray-900 dark:text-gray-100">
                          {formatCost(llmLogs.reduce((sum, log) => sum + (log.estimatedCost || 0), 0))}
                        </p>
                      </div>
                      <div className="text-center">
                        <p className="text-xs text-gray-500 mb-1">平均延迟</p>
                        <p className="text-xl font-bold text-gray-900 dark:text-gray-100">
                          {formatDuration(llmLogs.reduce((sum, log) => sum + (log.latencyMs || 0), 0) / llmLogs.length)}
                        </p>
                      </div>
                    </div>
                  </CardContent>
                </Card>
              )}
            </>
          )}
        </main>
      </div>
    </div>
  );
}
