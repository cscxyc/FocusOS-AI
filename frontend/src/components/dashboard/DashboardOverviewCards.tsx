"use client";

import * as React from "react";
import { Card, CardContent } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { useDashboardOverview } from "@/hooks/useDashboardOverview";
import type {
  ResumeSummary,
  CareerSummary,
  InterviewSummary,
  GrowthSummary,
} from "@/lib/types";
import {
  FileText,
  Target,
  Video,
  TrendingUp,
  Brain,
  Loader2,
  AlertCircle,
} from "lucide-react";

/** 单个状态卡片 */
function StatCard({
  icon: Icon,
  label,
  value,
  subtext,
  accent = "brand",
  isLoading,
}: {
  icon: React.ElementType;
  label: string;
  value: string | number | null;
  subtext?: string;
  accent?: "brand" | "accent" | "emerald" | "amber" | "blue";
  isLoading?: boolean;
}) {
  const accentColors: Record<string, string> = {
    brand: "from-brand-500/10 to-brand-500/5 text-brand-600 dark:text-brand-400",
    accent: "from-accent-500/10 to-accent-500/5 text-accent-600 dark:text-accent-400",
    emerald: "from-emerald-500/10 to-emerald-500/5 text-emerald-600 dark:text-emerald-400",
    amber: "from-amber-500/10 to-amber-500/5 text-amber-600 dark:text-amber-400",
    blue: "from-blue-500/10 to-blue-500/5 text-blue-600 dark:text-blue-400",
  };

  return (
    <Card className="overflow-hidden hover:shadow-md transition-shadow">
      <CardContent className="p-5">
        <div className="flex items-start justify-between">
          <div className="flex-1">
            <p className="text-xs font-medium text-gray-500 dark:text-gray-400 mb-2">
              {label}
            </p>
            {isLoading ? (
              <div className="h-8 w-20 bg-gray-100 dark:bg-gray-800 rounded animate-pulse" />
            ) : value == null ? (
              <p className="text-2xl font-bold text-gray-300 dark:text-gray-700">—</p>
            ) : (
              <p className="text-2xl font-bold text-gray-900 dark:text-gray-100">
                {value}
              </p>
            )}
            {subtext && (
              <p className="mt-1 text-xs text-gray-400 dark:text-gray-500 truncate">
                {subtext}
              </p>
            )}
          </div>
          <div className={`rounded-xl bg-gradient-to-br p-2.5 ${accentColors[accent]}`}>
            <Icon className="h-5 w-5" />
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

/** 简历评分卡片 */
function ResumeScoreCard({ data, isLoading }: { data?: ResumeSummary; isLoading: boolean }) {
  return (
    <StatCard
      icon={FileText}
      label="简历评分"
      value={data?.score != null ? `${data.score}/100` : null}
      subtext={data?.versionName || data?.targetPosition || (data?.totalVersions ? `${data.totalVersions} 个版本` : "暂无简历")}
      accent="brand"
      isLoading={isLoading}
    />
  );
}

/** JD 匹配最高分卡片 */
function MatchScoreCard({ data, isLoading }: { data?: CareerSummary; isLoading: boolean }) {
  return (
    <StatCard
      icon={Target}
      label="JD 匹配最高分"
      value={data?.matchScore != null ? `${data.matchScore}/100` : null}
      subtext={data?.jobTitle ? `${data.jobTitle}${data.company ? ` @ ${data.company}` : ""}` : (data?.totalReports ? `${data.totalReports} 份报告` : "暂无分析")}
      accent="emerald"
      isLoading={isLoading}
    />
  );
}

/** 模拟面试成绩卡片 */
function InterviewScoreCard({ data, isLoading }: { data?: InterviewSummary; isLoading: boolean }) {
  const value = data?.averageScore != null ? `${data.averageScore}/100` : null;
  const subtext = data?.completedSessions
    ? `已完成 ${data.completedSessions} 场${data.latestJobTitle ? ` · ${data.latestJobTitle}` : ""}`
    : data?.totalSessions
    ? `${data.totalSessions} 场会话`
    : "暂无面试";
  return (
    <StatCard
      icon={Video}
      label="模拟面试成绩"
      value={value}
      subtext={subtext}
      accent="blue"
      isLoading={isLoading}
    />
  );
}

/** 学习成长进度卡片 */
function GrowthProgressCard({ data, isLoading }: { data?: GrowthSummary; isLoading: boolean }) {
  return (
    <StatCard
      icon={TrendingUp}
      label="学习成长进度"
      value={data ? `${data.completedWeeks}/${data.totalWeeks} 周` : null}
      subtext={data ? `${data.progressPercent}% 完成${data.targetPosition ? ` · ${data.targetPosition}` : ""}` : "暂无计划"}
      accent="amber"
      isLoading={isLoading}
    />
  );
}

/** Memory 数量卡片 */
function MemoryCountCard({ count, isLoading }: { count?: number; isLoading: boolean }) {
  return (
    <StatCard
      icon={Brain}
      label="Memory 数量"
      value={count != null ? count : null}
      subtext="活跃记忆条数"
      accent="accent"
      isLoading={isLoading}
    />
  );
}

/** 骨架屏 */
function CardsSkeleton() {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5 gap-4">
      {[1, 2, 3, 4, 5].map((i) => (
        <Card key={i} className="overflow-hidden">
          <CardContent className="p-5">
            <div className="flex items-start justify-between">
              <div className="flex-1 space-y-2">
                <div className="h-3 w-16 bg-gray-100 dark:bg-gray-800 rounded animate-pulse" />
                <div className="h-8 w-20 bg-gray-100 dark:bg-gray-800 rounded animate-pulse" />
                <div className="h-3 w-24 bg-gray-50 dark:bg-gray-900 rounded animate-pulse" />
              </div>
              <div className="h-10 w-10 bg-gray-100 dark:bg-gray-800 rounded-xl animate-pulse" />
            </div>
          </CardContent>
        </Card>
      ))}
    </div>
  );
}

export function DashboardOverviewCards() {
  const { data, isLoading, isError, refetch } = useDashboardOverview();

  if (isLoading) {
    return <CardsSkeleton />;
  }

  if (isError) {
    return (
      <Card>
        <CardContent className="p-8">
          <div className="flex flex-col items-center text-center">
            <AlertCircle className="mb-2 h-8 w-8 text-red-400" />
            <p className="text-sm text-gray-500 mb-3">加载 Dashboard 数据失败</p>
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

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5 gap-4">
      <ResumeScoreCard data={data?.resumeScore} isLoading={false} />
      <MatchScoreCard data={data?.highestMatchScore} isLoading={false} />
      <InterviewScoreCard data={data?.interviewScore} isLoading={false} />
      <GrowthProgressCard data={data?.growthProgress} isLoading={false} />
      <MemoryCountCard count={data?.memoryCount} isLoading={false} />
    </div>
  );
}

/** 最近活动列表 */
export function RecentActivities() {
  const { data, isLoading } = useDashboardOverview();

  if (isLoading) {
    return (
      <Card>
        <CardContent className="p-5 space-y-3">
          {[1, 2, 3, 4, 5].map((i) => (
            <div key={i} className="flex items-center gap-3 animate-pulse">
              <div className="h-8 w-8 bg-gray-100 dark:bg-gray-800 rounded-full" />
              <div className="flex-1 space-y-1">
                <div className="h-3 w-32 bg-gray-100 dark:bg-gray-800 rounded" />
                <div className="h-2 w-48 bg-gray-50 dark:bg-gray-900 rounded" />
              </div>
            </div>
          ))}
        </CardContent>
      </Card>
    );
  }

  const activities = data?.recentActivities ?? [];

  if (activities.length === 0) {
    return (
      <Card>
        <CardContent className="p-8">
          <div className="flex flex-col items-center text-center">
            <div className="mb-3 rounded-full bg-gradient-to-r from-brand-500/10 to-accent-500/10 p-3">
              <Target className="h-6 w-6 text-brand-500" />
            </div>
            <p className="text-sm font-medium text-gray-900 dark:text-gray-100">暂无最近活动</p>
            <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">
              启动 Career Workflow 或上传简历后，活动将显示在这里
            </p>
          </div>
        </CardContent>
      </Card>
    );
  }

  const typeIcons: Record<string, React.ElementType> = {
    CAREER_ANALYSIS: Target,
    RESUME_OPTIMIZATION: FileText,
    INTERVIEW: Video,
    GROWTH_PLAN: TrendingUp,
    WORKFLOW: Brain,
  };

  return (
    <Card>
      <CardContent className="p-5">
        <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-4">
          最近活动
        </h3>
        <div className="space-y-3">
          {activities.slice(0, 8).map((activity, idx) => {
            const Icon = typeIcons[activity.type] || Brain;
            return (
              <div key={idx} className="flex items-start gap-3 group">
                <div className="mt-0.5 rounded-lg bg-gradient-to-br from-brand-500/10 to-accent-500/10 p-1.5 shrink-0">
                  <Icon className="h-4 w-4 text-brand-500" />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-medium text-gray-900 dark:text-gray-100">
                      {activity.title}
                    </span>
                    {activity.status && (
                      <Badge
                        variant={
                          activity.status === "SUCCESS" || activity.status === "COMPLETED" || activity.status === "ACTIVE"
                            ? "success"
                            : activity.status === "FAILED"
                            ? "destructive"
                            : activity.status === "RUNNING" || activity.status === "IN_PROGRESS"
                            ? "default"
                            : "secondary"
                        }
                        className="text-xs"
                      >
                        {activity.status}
                      </Badge>
                    )}
                  </div>
                  <p className="text-xs text-gray-500 dark:text-gray-400 truncate">
                    {activity.description}
                  </p>
                  {activity.createdAt && (
                    <p className="text-xs text-gray-400 dark:text-gray-600 mt-0.5">
                      {new Date(activity.createdAt).toLocaleString("zh-CN")}
                    </p>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </CardContent>
    </Card>
  );
}
