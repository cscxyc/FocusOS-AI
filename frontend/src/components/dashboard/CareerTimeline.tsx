"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { useCareerTimeline } from "@/hooks/useDashboardOverview";
import type { TimelineStage } from "@/lib/types";
import {
  CheckCircle2,
  XCircle,
  Loader2,
  Circle,
  SkipForward,
  FileSearch,
  FileText,
  Brain,
  GraduationCap,
  MessageSquare,
  Video,
} from "lucide-react";

const STAGE_ICONS: Record<string, React.ElementType> = {
  CAREER_ANALYSIS: FileSearch,
  RESUME_OPTIMIZATION: FileText,
  SKILL_GAP_ANALYSIS: Brain,
  LEARNING_PLAN: GraduationCap,
  INTERVIEW_PREPARATION: MessageSquare,
  MOCK_INTERVIEW: Video,
};

function getStatusIcon(status: string) {
  switch (status) {
    case "SUCCESS":
      return <CheckCircle2 className="h-5 w-5 text-emerald-500" />;
    case "FAILED":
      return <XCircle className="h-5 w-5 text-red-500" />;
    case "RUNNING":
      return <Loader2 className="h-5 w-5 text-blue-500 animate-spin" />;
    case "PENDING":
      return <Circle className="h-5 w-5 text-amber-400" />;
    case "SKIPPED":
      return <SkipForward className="h-5 w-5 text-gray-400" />;
    default:
      return <Circle className="h-5 w-5 text-gray-300" />;
  }
}

function getStatusBadge(status: string) {
  switch (status) {
    case "SUCCESS":
      return <Badge variant="success">已完成</Badge>;
    case "FAILED":
      return <Badge variant="destructive">失败</Badge>;
    case "RUNNING":
      return <Badge variant="default">进行中</Badge>;
    case "PENDING":
      return <Badge variant="warning">待执行</Badge>;
    case "SKIPPED":
      return <Badge variant="secondary">未开始</Badge>;
    default:
      return <Badge variant="outline">{status}</Badge>;
  }
}

/** 骨架屏 */
function TimelineSkeleton() {
  return (
    <div className="space-y-4">
      {[1, 2, 3, 4, 5, 6].map((i) => (
        <div key={i} className="flex items-start gap-4 animate-pulse">
          <div className="h-10 w-10 rounded-full bg-gray-200 dark:bg-gray-800" />
          <div className="flex-1 space-y-2">
            <div className="h-4 w-32 bg-gray-200 dark:bg-gray-800 rounded" />
            <div className="h-3 w-64 bg-gray-100 dark:bg-gray-900 rounded" />
          </div>
        </div>
      ))}
    </div>
  );
}

/** 空状态 */
function EmptyState() {
  return (
    <div className="flex flex-col items-center justify-center py-12 text-center">
      <div className="mb-4 rounded-full bg-gradient-to-r from-brand-500/10 to-accent-500/10 p-4">
        <FileSearch className="h-8 w-8 text-brand-500" />
      </div>
      <h3 className="text-sm font-medium text-gray-900 dark:text-gray-100">尚未开始职业旅程</h3>
      <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
        启动 Career Workflow 后，这里将展示你的求职流程进度
      </p>
    </div>
  );
}

export function CareerTimeline() {
  const { data, isLoading, isError, refetch } = useCareerTimeline();

  if (isLoading) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="text-lg">Career Journey</CardTitle>
        </CardHeader>
        <CardContent>
          <TimelineSkeleton />
        </CardContent>
      </Card>
    );
  }

  if (isError) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="text-lg">Career Journey</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex flex-col items-center py-8 text-center">
            <XCircle className="mb-2 h-8 w-8 text-red-400" />
            <p className="text-sm text-gray-500 mb-3">加载时间线失败</p>
            <button
              onClick={() => refetch()}
              className="text-sm text-brand-500 hover:text-brand-600 font-medium"
            >
              重新加载
            </button>
          </div>
        </CardContent>
      </Card>
    );
  }

  const stages = data?.stages ?? [];
  const hasData = stages.some((s) => s.status !== "SKIPPED");

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between">
          <CardTitle className="text-lg">Career Journey</CardTitle>
          {data?.workflowId && (
            <Badge variant="outline" className="font-mono text-xs">
              {data.workflowId}
            </Badge>
          )}
        </div>
      </CardHeader>
      <CardContent>
        {!hasData ? (
          <EmptyState />
        ) : (
          <div className="relative">
            {/* 连接线 */}
            <div className="absolute left-5 top-0 bottom-0 w-px bg-gray-200 dark:bg-gray-800" />

            <div className="space-y-6">
              {stages.map((stage: TimelineStage, index: number) => {
                const Icon = STAGE_ICONS[stage.stage] || Circle;
                return (
                  <div key={stage.stage} className="relative flex items-start gap-4">
                    {/* 节点图标 */}
                    <div className="relative z-10 flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-white dark:bg-gray-950 border-2 border-gray-200 dark:border-gray-800">
                      {stage.status === "SKIPPED" ? (
                        <Icon className="h-5 w-5 text-gray-300 dark:text-gray-700" />
                      ) : (
                        getStatusIcon(stage.status)
                      )}
                    </div>

                    {/* 内容 */}
                    <div className="flex-1 pb-2">
                      <div className="flex items-center gap-2 mb-1">
                        <span className="text-sm font-medium text-gray-900 dark:text-gray-100">
                          {stage.title}
                        </span>
                        {getStatusBadge(stage.status)}
                      </div>
                      <p className="text-xs text-gray-500 dark:text-gray-400 mb-1">
                        {stage.description}
                      </p>
                      {stage.createdAt && (
                        <p className="text-xs text-gray-400 dark:text-gray-600">
                          {new Date(stage.createdAt).toLocaleString("zh-CN")}
                        </p>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
