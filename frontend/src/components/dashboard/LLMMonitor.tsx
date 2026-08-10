"use client";

import * as React from "react";
import { Card, CardContent } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { useLLMLogSummary, useLLMRecentLogs } from "@/hooks/useLLMLogs";
import {
  Activity,
  CheckCircle2,
  XCircle,
  Clock,
  Coins,
  Cpu,
  Zap,
  Loader2,
  AlertCircle,
} from "lucide-react";

/**
 * Sprint 7-C-B: LLM 调用监控 Dashboard 组件
 * <p>
 * 展示用户的 LLM 调用统计：
 * - 总调用次数 / 成功 / 失败
 * - 总 Token（输入 + 输出）
 * - 总耗时
 * - 按 Agent 分组统计
 * - 最近调用记录列表
 */
export function LLMMonitor() {
  const summaryQuery = useLLMLogSummary();
  const recentQuery = useLLMRecentLogs();

  if (summaryQuery.isLoading) {
    return (
      <div className="flex items-center justify-center py-8 text-gray-400">
        <Loader2 className="h-5 w-5 animate-spin mr-2" />
        加载 LLM 调用统计...
      </div>
    );
  }

  if (summaryQuery.isError || !summaryQuery.data) {
    return (
      <div className="flex items-center justify-center py-8 text-orange-400">
        <AlertCircle className="h-5 w-5 mr-2" />
        暂无 LLM 调用数据
      </div>
    );
  }

  const s = summaryQuery.data;
  const totalTokens = s.totalInputTokens + s.totalOutputTokens;

  return (
    <div className="space-y-4">
      {/* 统计卡片 */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <StatCard
          icon={<Activity className="h-4 w-4" />}
          label="调用次数"
          value={s.totalCalls.toString()}
          sub={`${s.successCalls} 成功 / ${s.failedCalls} 失败`}
          color="text-blue-400"
        />
        <StatCard
          icon={<Coins className="h-4 w-4" />}
          label="Token 用量"
          value={formatTokens(totalTokens)}
          sub={`入 ${formatTokens(s.totalInputTokens)} / 出 ${formatTokens(s.totalOutputTokens)}`}
          color="text-purple-400"
        />
        <StatCard
          icon={<Clock className="h-4 w-4" />}
          label="总耗时"
          value={formatDuration(s.totalLatencyMs)}
          sub={s.totalCalls > 0 ? `平均 ${formatDuration(s.totalLatencyMs / s.totalCalls)}` : "-"}
          color="text-green-400"
        />
        <StatCard
          icon={<Cpu className="h-4 w-4" />}
          label="Agent 数"
          value={s.byAgent?.length?.toString() ?? "0"}
          sub={s.byAgent?.map((a) => a.agentType).join(", ") || "-"}
          color="text-orange-400"
        />
      </div>

      {/* 按 Agent 分组统计 */}
      {s.byAgent && s.byAgent.length > 0 && (
        <Card>
          <CardContent className="p-4">
            <h3 className="text-sm font-semibold text-gray-200 mb-3 flex items-center gap-2">
              <Zap className="h-4 w-4 text-yellow-400" />
              Agent 调用分布
            </h3>
            <div className="space-y-2">
              {s.byAgent.map((agent) => {
                const maxCalls = Math.max(...s.byAgent.map((a) => a.callCount), 1);
                const widthPct = (agent.callCount / maxCalls) * 100;
                return (
                  <div key={agent.agentType} className="flex items-center gap-3">
                    <div className="w-32 text-xs text-gray-400 font-mono truncate">
                      {agent.agentType}
                    </div>
                    <div className="flex-1 h-6 bg-gray-800 rounded relative overflow-hidden">
                      <div
                        className="h-full bg-gradient-to-r from-blue-600 to-purple-600 rounded transition-all"
                        style={{ width: `${widthPct}%` }}
                      />
                      <div className="absolute inset-0 flex items-center justify-between px-2 text-xs text-white">
                        <span>{agent.callCount} 次</span>
                        <span>
                          {formatTokens(agent.totalTokens)} tokens · {formatDuration(agent.avgLatencyMs)}
                        </span>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </CardContent>
        </Card>
      )}

      {/* 最近调用记录 */}
      {recentQuery.data && recentQuery.data.length > 0 && (
        <Card>
          <CardContent className="p-4">
            <h3 className="text-sm font-semibold text-gray-200 mb-3 flex items-center gap-2">
              <Activity className="h-4 w-4 text-blue-400" />
              最近调用记录
            </h3>
            <div className="space-y-1.5 max-h-64 overflow-y-auto">
              {recentQuery.data.slice(0, 20).map((log) => (
                <div
                  key={log.id}
                  className="flex items-center gap-3 px-2 py-1.5 rounded bg-gray-800/40 hover:bg-gray-800/60 transition-colors text-xs"
                >
                  {log.success ? (
                    <CheckCircle2 className="h-3.5 w-3.5 text-green-400 flex-shrink-0" />
                  ) : (
                    <XCircle className="h-3.5 w-3.5 text-red-400 flex-shrink-0" />
                  )}
                  <Badge variant="secondary" className="text-xs font-mono">
                    {log.agentType}
                  </Badge>
                  <span className="text-gray-500 font-mono">{log.model}</span>
                  <span className="text-gray-400">
                    {log.inputTokens ?? 0} + {log.outputTokens ?? 0} tok
                  </span>
                  <span className="text-gray-500">{formatDuration(log.latencyMs ?? 0)}</span>
                  <span className="text-gray-600 ml-auto">
                    {new Date(log.createdAt).toLocaleTimeString("zh-CN")}
                  </span>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}

function StatCard({
  icon,
  label,
  value,
  sub,
  color,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
  sub: string;
  color: string;
}) {
  return (
    <Card>
      <CardContent className="p-3">
        <div className="flex items-center gap-2 mb-1">
          <span className={color}>{icon}</span>
          <span className="text-xs text-gray-400">{label}</span>
        </div>
        <div className="text-lg font-bold text-gray-100">{value}</div>
        <div className="text-xs text-gray-500 mt-0.5">{sub}</div>
      </CardContent>
    </Card>
  );
}

function formatTokens(n: number): string {
  if (n >= 10000) return `${(n / 1000).toFixed(1)}K`;
  if (n >= 1000) return `${(n / 1000).toFixed(2)}K`;
  return n.toFixed(0);
}

function formatDuration(ms: number): string {
  if (ms >= 60000) return `${(ms / 60000).toFixed(1)}min`;
  if (ms >= 1000) return `${(ms / 1000).toFixed(1)}s`;
  return `${ms.toFixed(0)}ms`;
}
