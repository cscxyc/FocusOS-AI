"use client";

import * as React from "react";
import { Card, CardContent } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import {
  Sparkles,
  Play,
  Loader2,
  CheckCircle2,
  XCircle,
  Clock,
  RefreshCw,
  Activity,
  Cpu,
} from "lucide-react";
import { getToken } from "@/lib/auth";

// ============ 类型定义 ============
interface WorkflowEvent {
  event: string;
  workflowId: string;
  taskId?: number;
  taskType?: string;
  agentType?: string;
  task?: string;
  status?: string;
  progress?: number;
  message?: string;
  durationMs?: number;
  errorMessage?: string;
  timestamp?: string;
  summary?: string;
  completedTasks?: number;
  totalTasks?: number;
}

interface TaskState {
  taskId?: number;
  taskType?: string;
  agentType?: string;
  task?: string;
  status: "PENDING" | "RUNNING" | "SUCCESS" | "FAILED";
  durationMs?: number;
  errorMessage?: string;
  message?: string;
}

interface WorkflowState {
  workflowId: string;
  status: "STARTED" | "RUNNING" | "SUCCESS" | "PARTIAL" | "FAILED";
  progress: number;
  currentTask?: string;
  currentAgent?: string;
  tasks: TaskState[];
  summary?: string;
  totalTasks?: number;
  completedTasks?: number;
  durationMs?: number;
  errorMessage?: string;
}

// ============ 主组件 ============
export function WorkflowMonitor() {
  const [goal, setGoal] = React.useState("根据我的经历规划AI应用开发转型路线");
  const [workflow, setWorkflow] = React.useState<WorkflowState | null>(null);
  const [isStarting, setIsStarting] = React.useState(false);
  const [eventLog, setEventLog] = React.useState<WorkflowEvent[]>([]);
  const [history, setHistory] = React.useState<any[]>([]);
  const eventSourceRef = React.useRef<EventSource | null>(null);
  const startTimeRef = React.useRef<number>(0);
  const [elapsedMs, setElapsedMs] = React.useState(0);

  // 轮询已用时间
  React.useEffect(() => {
    if (!workflow || workflow.status === "SUCCESS" || workflow.status === "FAILED" || workflow.status === "PARTIAL") {
      return;
    }
    const timer = setInterval(() => {
      setElapsedMs(Date.now() - startTimeRef.current);
    }, 500);
    return () => clearInterval(timer);
  }, [workflow?.status]);

  // 加载历史 workflow 列表
  const loadHistory = React.useCallback(async () => {
    try {
      const res = await fetch("http://localhost:8080/workflow/history", {
        headers: { Authorization: `Bearer ${getToken()}` },
      });
      const json = await res.json();
      if (json.code === 200) {
        setHistory(json.data || []);
      }
    } catch (e) {
      console.error("Failed to load history:", e);
    }
  }, []);

  React.useEffect(() => {
    loadHistory();
  }, [loadHistory]);

  // 清理 EventSource
  React.useEffect(() => {
    return () => {
      eventSourceRef.current?.close();
    };
  }, []);

  // 启动 workflow
  const startWorkflow = async () => {
    if (!goal.trim()) return;
    setIsStarting(true);
    setWorkflow(null);
    setEventLog([]);
    setElapsedMs(0);

    try {
      const res = await fetch("http://localhost:8080/workflow/execute", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${getToken()}`,
        },
        body: JSON.stringify({ goal }),
      });
      const json = await res.json();
      if (json.code !== 200) {
        alert("启动失败: " + json.message);
        setIsStarting(false);
        return;
      }

      const { workflowId, startElapsedMs } = json.data;
      startTimeRef.current = Date.now() - (startElapsedMs || 0);
      setWorkflow({
        workflowId,
        status: "STARTED",
        progress: 0,
        tasks: [],
        totalTasks: 0,
        completedTasks: 0,
      });
      setIsStarting(false);

      // 订阅 SSE
      subscribeSSE(workflowId);
    } catch (e: any) {
      alert("请求失败: " + e.message);
      setIsStarting(false);
    }
  };

  // SSE 订阅
  const subscribeSSE = (workflowId: string) => {
    eventSourceRef.current?.close();
    const token = getToken();
    const url = `http://localhost:8080/api/workflow/${workflowId}/events?token=${encodeURIComponent(token || "")}`;
    const es = new EventSource(url);
    eventSourceRef.current = es;

    const handleEvent = (eventType: string) => (e: MessageEvent) => {
      try {
        const data: WorkflowEvent = JSON.parse(e.data);
        setEventLog((prev) => [...prev, data]);
        updateWorkflowState(data);
      } catch (err) {
        console.error("Parse SSE event failed:", err);
      }
    };

    es.addEventListener("workflow_started", handleEvent("workflow_started"));
    es.addEventListener("task_started", handleEvent("task_started"));
    es.addEventListener("task_completed", handleEvent("task_completed"));
    es.addEventListener("workflow_completed", handleEvent("workflow_completed"));
    es.addEventListener("workflow_failed", handleEvent("workflow_failed"));
    es.onerror = () => {
      // SSE 错误不一定是失败，可能是断线，关闭重连交给用户
      es.close();
    };
  };

  // 根据 SSE 事件更新 workflow 状态
  const updateWorkflowState = (event: WorkflowEvent) => {
    setWorkflow((prev) => {
      if (!prev) return prev;
      const next: WorkflowState = { ...prev, tasks: [...prev.tasks] };

      if (event.event === "workflow_started") {
        next.status = "RUNNING";
        next.totalTasks = event.totalTasks;
        next.progress = event.progress ?? 0;
      } else if (event.event === "task_started") {
        next.status = "RUNNING";
        next.progress = event.progress ?? next.progress;
        next.currentTask = event.task;
        next.currentAgent = event.agentType;
        // 添加或更新任务
        const idx = next.tasks.findIndex((t) => t.taskId === event.taskId);
        const taskState: TaskState = {
          taskId: event.taskId,
          taskType: event.taskType,
          agentType: event.agentType,
          task: event.task,
          status: "RUNNING",
          message: event.message,
        };
        if (idx >= 0) next.tasks[idx] = taskState;
        else next.tasks.push(taskState);
      } else if (event.event === "task_completed") {
        next.progress = event.progress ?? next.progress;
        next.completedTasks = (next.completedTasks ?? 0) + (event.status === "SUCCESS" || event.status === "FAILED" ? 1 : 0);
        const idx = next.tasks.findIndex((t) => t.taskId === event.taskId);
        const taskState: TaskState = {
          taskId: event.taskId,
          taskType: event.taskType,
          agentType: event.agentType,
          task: event.task,
          status: (event.status as any) || "SUCCESS",
          durationMs: event.durationMs,
          errorMessage: event.errorMessage,
          message: event.message,
        };
        if (idx >= 0) next.tasks[idx] = taskState;
        else next.tasks.push(taskState);
      } else if (event.event === "workflow_completed") {
        next.status = event.status as any;
        next.progress = 100;
        next.summary = event.summary;
        next.durationMs = event.durationMs;
        next.totalTasks = event.totalTasks;
        next.completedTasks = event.completedTasks;
        setElapsedMs(event.durationMs ?? 0);
        // 重新加载历史
        setTimeout(loadHistory, 500);
        eventSourceRef.current?.close();
      } else if (event.event === "workflow_failed") {
        next.status = "FAILED";
        next.errorMessage = event.errorMessage || event.message;
        eventSourceRef.current?.close();
      }
      return next;
    });
  };

  // 恢复历史 workflow（页面刷新后）
  const restoreWorkflow = async (workflowId: string) => {
    try {
      const res = await fetch(`http://localhost:8080/workflow/${workflowId}`, {
        headers: { Authorization: `Bearer ${getToken()}` },
      });
      const json = await res.json();
      if (json.code !== 200 || !json.data) {
        alert("无法恢复: " + json.message);
        return;
      }
      const wf = json.data;
      const restored: WorkflowState = {
        workflowId: wf.workflowId,
        status: wf.status,
        progress: wf.status === "SUCCESS" ? 100 : wf.status === "RUNNING" ? 50 : 0,
        tasks: (wf.tasks || []).map((t: any) => ({
          taskId: t.id,
          taskType: t.taskType,
          agentType: t.agentType,
          task: t.goal,
          status: t.status,
          durationMs: t.durationMs,
          errorMessage: t.errorMessage,
        })),
        totalTasks: wf.totalTasks,
        completedTasks: wf.successTasks,
      };
      setWorkflow(restored);
      setEventLog([]);
      // 如果仍在运行，订阅 SSE（含历史补发）
      if (wf.status === "RUNNING") {
        subscribeSSE(wf.workflowId);
      }
    } catch (e: any) {
      alert("恢复失败: " + e.message);
    }
  };

  // ============ 渲染 ============
  const formatMs = (ms?: number) => {
    if (!ms && ms !== 0) return "-";
    if (ms < 1000) return `${ms}ms`;
    return `${(ms / 1000).toFixed(1)}s`;
  };

  const statusColor = (s?: string) => {
    switch (s) {
      case "SUCCESS": return "text-green-600";
      case "FAILED": return "text-red-600";
      case "RUNNING": return "text-blue-600";
      default: return "text-gray-500";
    }
  };

  const agentIcon = (agent?: string) => {
    switch (agent) {
      case "career": return "🎯";
      case "learning": return "📚";
      case "rag": return "🔍";
      case "master": return "🧠";
      default: return "⚙️";
    }
  };

  return (
    <div className="space-y-6">
      {/* 输入区 */}
      <Card>
        <CardContent className="p-6">
          <div className="flex items-center gap-3 mb-4">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-brand-500 to-accent-500">
              <Sparkles className="h-5 w-5 text-white" />
            </div>
            <div>
              <h3 className="font-semibold text-gray-900 dark:text-white">
                AI Workflow Monitor
              </h3>
              <p className="text-xs text-gray-500 dark:text-gray-400">
                Sprint 6-B · 异步多 Agent 协作 · SSE 实时进度
              </p>
            </div>
          </div>
          <div className="flex gap-3">
            <input
              type="text"
              value={goal}
              onChange={(e) => setGoal(e.target.value)}
              placeholder="输入你的目标，如：根据我的经历规划AI应用开发转型路线"
              className="flex-1 px-4 py-2 rounded-xl border border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900 text-sm focus:outline-none focus:ring-2 focus:ring-brand-500"
              disabled={isStarting || workflow?.status === "RUNNING" || workflow?.status === "STARTED"}
            />
            <Button
              onClick={startWorkflow}
              disabled={isStarting || !goal.trim() || workflow?.status === "RUNNING" || workflow?.status === "STARTED"}
            >
              {isStarting ? (
                <Loader2 className="h-4 w-4 mr-2 animate-spin" />
              ) : (
                <Play className="h-4 w-4 mr-2" />
              )}
              启动 Workflow
            </Button>
            <Button variant="outline" onClick={loadHistory}>
              <RefreshCw className="h-4 w-4 mr-2" />
              刷新历史
            </Button>
          </div>
        </CardContent>
      </Card>

      {/* 执行面板 */}
      {workflow && (
        <Card className="border-brand-200 dark:border-brand-800">
          <CardContent className="p-6">
            {/* 标题区 */}
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-3">
                <div className={`flex h-10 w-10 items-center justify-center rounded-xl ${
                  workflow.status === "SUCCESS" ? "bg-green-100 dark:bg-green-900/30"
                  : workflow.status === "FAILED" ? "bg-red-100 dark:bg-red-900/30"
                  : "bg-brand-100 dark:bg-brand-900/30"
                }`}>
                  {workflow.status === "SUCCESS" ? <CheckCircle2 className="h-5 w-5 text-green-600" />
                  : workflow.status === "FAILED" ? <XCircle className="h-5 w-5 text-red-600" />
                  : <Loader2 className="h-5 w-5 text-brand-500 animate-spin" />}
                </div>
                <div>
                  <h3 className="font-semibold text-gray-900 dark:text-white">
                    工作流 #{workflow.workflowId}
                  </h3>
                  <p className="text-xs text-gray-500">
                    状态: <span className={statusColor(workflow.status)}>{workflow.status}</span>
                    {" · "}耗时: <span className="font-mono">{formatMs(elapsedMs || workflow.durationMs || 0)}</span>
                    {" · "}{workflow.completedTasks}/{workflow.totalTasks} 任务
                  </p>
                </div>
              </div>
              <Badge variant="secondary" className="text-xs">
                {workflow.progress}%
              </Badge>
            </div>

            {/* 进度条 */}
            <div className="w-full h-3 bg-gray-200 dark:bg-gray-800 rounded-full overflow-hidden mb-6">
              <div
                className={`h-full transition-all duration-500 ${
                  workflow.status === "FAILED" ? "bg-red-500"
                  : workflow.status === "SUCCESS" ? "bg-green-500"
                  : "bg-gradient-to-r from-brand-500 to-accent-500"
                }`}
                style={{ width: `${workflow.progress}%` }}
              />
            </div>

            {/* 任务时间线 */}
            <div className="space-y-3">
              <h4 className="text-sm font-medium text-gray-700 dark:text-gray-300 flex items-center gap-2">
                <Activity className="h-4 w-4" />
                任务流水线
              </h4>
              {workflow.tasks.length === 0 ? (
                <p className="text-sm text-gray-500 text-center py-4">
                  等待任务规划中...
                </p>
              ) : (
                workflow.tasks.map((task, i) => (
                  <div
                    key={task.taskId ?? i}
                    className={`flex items-start gap-3 p-3 rounded-xl border ${
                      task.status === "SUCCESS" ? "border-green-200 bg-green-50/50 dark:border-green-900 dark:bg-green-950/20"
                      : task.status === "FAILED" ? "border-red-200 bg-red-50/50 dark:border-red-900 dark:bg-red-950/20"
                      : task.status === "RUNNING" ? "border-brand-300 bg-brand-50/50 dark:border-brand-800 dark:bg-brand-950/20"
                      : "border-gray-200 dark:border-gray-800"
                    }`}
                  >
                    <div className="text-xl mt-0.5">{agentIcon(task.agentType)}</div>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2">
                        <p className="text-sm font-medium text-gray-900 dark:text-white truncate">
                          {task.task || task.taskType}
                        </p>
                        <Badge variant="secondary" className="text-[10px] shrink-0">
                          {task.taskType}
                        </Badge>
                      </div>
                      <p className="text-xs text-gray-500 mt-0.5">
                        <Cpu className="inline h-3 w-3 mr-1" />
                        Agent: <span className="font-mono">{task.agentType}</span>
                        {task.durationMs != null && (
                          <span className="ml-2">
                            <Clock className="inline h-3 w-3 mr-1" />
                            {formatMs(task.durationMs)}
                          </span>
                        )}
                        {task.status === "RUNNING" && (
                          <span className="ml-2 text-brand-500">
                            <Loader2 className="inline h-3 w-3 mr-1 animate-spin" />
                            执行中...
                          </span>
                        )}
                      </p>
                      {task.errorMessage && (
                        <p className="text-xs text-red-600 mt-1">⚠ {task.errorMessage}</p>
                      )}
                    </div>
                    <div className={statusColor(task.status)}>
                      {task.status === "SUCCESS" ? <CheckCircle2 className="h-5 w-5" />
                      : task.status === "FAILED" ? <XCircle className="h-5 w-5" />
                      : task.status === "RUNNING" ? <Loader2 className="h-5 w-5 animate-spin" />
                      : <Clock className="h-5 w-5" />}
                    </div>
                  </div>
                ))
              )}
            </div>

            {/* 总结 */}
            {workflow.summary && (
              <div className="mt-6 p-4 rounded-xl bg-gradient-to-br from-brand-50 to-accent-50 dark:from-brand-950/30 dark:to-accent-950/30 border border-brand-200 dark:border-brand-800">
                <h4 className="text-sm font-semibold text-gray-900 dark:text-white mb-2 flex items-center gap-2">
                  <Sparkles className="h-4 w-4" />
                  MasterAgent 综合总结
                </h4>
                <div className="text-sm text-gray-700 dark:text-gray-300 whitespace-pre-wrap max-h-80 overflow-y-auto">
                  {workflow.summary}
                </div>
              </div>
            )}
          </CardContent>
        </Card>
      )}

      {/* 事件日志 */}
      {eventLog.length > 0 && (
        <Card>
          <CardContent className="p-6">
            <h4 className="text-sm font-medium text-gray-700 dark:text-gray-300 mb-3">
              SSE 事件日志 ({eventLog.length})
            </h4>
            <div className="space-y-1 max-h-60 overflow-y-auto font-mono text-xs">
              {eventLog.map((e, i) => (
                <div key={i} className="flex items-start gap-2 p-1.5 rounded hover:bg-gray-50 dark:hover:bg-gray-900">
                  <span className="text-gray-400 shrink-0">[{e.timestamp?.slice(11, 19) || "-"}]</span>
                  <Badge variant="secondary" className="text-[10px] shrink-0">{e.event}</Badge>
                  <span className="text-gray-700 dark:text-gray-300">{e.message}</span>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}

      {/* 历史工作流 */}
      {history.length > 0 && (
        <Card>
          <CardContent className="p-6">
            <h4 className="text-sm font-medium text-gray-700 dark:text-gray-300 mb-3">
              历史 Workflow ({history.length})
            </h4>
            <div className="space-y-2">
              {history.slice(0, 10).map((wf) => (
                <div
                  key={wf.workflowId}
                  className="flex items-center gap-3 p-2 rounded-lg border border-gray-200 dark:border-gray-800 hover:bg-gray-50 dark:hover:bg-gray-900 cursor-pointer"
                  onClick={() => restoreWorkflow(wf.workflowId)}
                >
                  <div className={statusColor(wf.status)}>
                    {wf.status === "SUCCESS" ? <CheckCircle2 className="h-4 w-4" />
                    : wf.status === "FAILED" ? <XCircle className="h-4 w-4" />
                    : <Loader2 className="h-4 w-4 animate-spin" />}
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm text-gray-900 dark:text-white truncate">{wf.userGoal}</p>
                    <p className="text-xs text-gray-500">
                      #{wf.workflowId} · {wf.successTasks}/{wf.totalTasks} · {new Date(wf.createdAt).toLocaleString("zh-CN")}
                    </p>
                  </div>
                  <Badge variant="secondary" className="text-[10px]">{wf.status}</Badge>
                </div>
              ))}
            </div>
            <p className="text-xs text-gray-400 mt-2">点击历史 workflow 可恢复查看详情</p>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
