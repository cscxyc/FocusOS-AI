"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { Sidebar } from "@/components/layout/Sidebar";
import { Header } from "@/components/layout/Header";
import { Card, CardContent } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import { useDashboard } from "@/hooks/useDashboard";
import { useAuthStore } from "@/store/authStore";
import { useDashboardOverview } from "@/hooks/useDashboardOverview";
import {
  DashboardOverviewCards,
  RecentActivities,
} from "@/components/dashboard/DashboardOverviewCards";
import { CareerTimeline } from "@/components/dashboard/CareerTimeline";
import { Clock, BookOpen, Calendar, Sparkles, CheckCircle2, Loader2, Rocket, FileText } from "lucide-react";

export default function DashboardPage() {
  const { data, aiRecommendation, isLoading, isError, error, refetch, isRecommendationLoading } = useDashboard();
  const { data: overview } = useDashboardOverview();
  const user = useAuthStore((s) => s.user);
  const router = useRouter();

  const getGreeting = () => {
    const hour = new Date().getHours();
    if (hour < 6) return "夜深了";
    if (hour < 12) return "早上好";
    if (hour < 14) return "中午好";
    if (hour < 18) return "下午好";
    return "晚上好";
  };

  const learningStats = (data as any)?.learningStats;
  const todayMinutes: number = learningStats?.todayMinutes ?? 0;
  const activePlans: number = learningStats?.activePlans ?? 0;
  const todayEvents: any[] = (data as any)?.todayEvents ?? [];

  const formatMinutes = (mins: number) => {
    if (mins <= 0) return "0 分钟";
    if (mins < 60) return `${mins} 分钟`;
    const h = Math.floor(mins / 60);
    const m = mins % 60;
    return m === 0 ? `${h} 小时` : `${h} 小时 ${m} 分`;
  };

  const formatTime = (iso: string) => {
    try {
      return new Date(iso).toLocaleTimeString("zh-CN", {
        hour: "2-digit",
        minute: "2-digit",
      });
    } catch {
      return "";
    }
  };

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-950">
      <Sidebar />
      <div className="lg:pl-64">
        <Header />
        <main className="p-6 space-y-6">
          {/* 欢迎栏 */}
          <div className="flex flex-wrap items-center justify-between gap-4">
            <div>
              <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">
                {getGreeting()}，{user?.username || "用户"}
              </h1>
              <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
                欢迎回到 FocusOS AI · 你的 AI 职业操作系统
              </p>
            </div>
            <Button
              onClick={() => router.push("/career")}
              className="bg-gradient-to-r from-brand-500 to-accent-500"
            >
              <Rocket className="mr-1.5 h-4 w-4" />
              启动 Career Workflow
            </Button>
          </div>

          {/* Sprint 9-A: 职业状态卡片（5 张） */}
          <DashboardOverviewCards />

          {/* 双栏布局：Career Timeline + 最近活动 */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <CareerTimeline />
            <RecentActivities />
          </div>

          {/* 学习统计 + 今日事件（保留原有 Sprint 6-A 内容） */}
          {isLoading ? (
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              {[1, 2, 3].map((i) => (
                <Card key={i}>
                  <CardContent className="p-5">
                    <div className="space-y-3 animate-pulse">
                      <div className="h-4 w-20 bg-gray-100 dark:bg-gray-800 rounded" />
                      <div className="h-8 w-24 bg-gray-100 dark:bg-gray-800 rounded" />
                      <div className="h-3 w-16 bg-gray-50 dark:bg-gray-900 rounded" />
                    </div>
                  </CardContent>
                </Card>
              ))}
            </div>
          ) : isError ? (
            <Card>
              <CardContent className="p-8">
                <div className="flex flex-col items-center text-center">
                  <p className="text-sm text-gray-500 mb-3">加载学习数据失败</p>
                  <button
                    onClick={() => refetch()}
                    className="text-sm text-brand-500 hover:text-brand-600 font-medium"
                  >
                    重新加载
                  </button>
                </div>
              </CardContent>
            </Card>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              {/* 今日学习时长 */}
              <Card>
                <CardContent className="p-5">
                  <div className="flex items-start justify-between">
                    <div>
                      <p className="text-xs font-medium text-gray-500 dark:text-gray-400 mb-2">
                        今日学习
                      </p>
                      <p className="text-2xl font-bold text-gray-900 dark:text-gray-100">
                        {formatMinutes(todayMinutes)}
                      </p>
                    </div>
                    <div className="rounded-xl bg-gradient-to-br from-brand-500/10 to-brand-500/5 p-2.5">
                      <BookOpen className="h-5 w-5 text-brand-500" />
                    </div>
                  </div>
                </CardContent>
              </Card>

              {/* 活跃计划 */}
              <Card>
                <CardContent className="p-5">
                  <div className="flex items-start justify-between">
                    <div>
                      <p className="text-xs font-medium text-gray-500 dark:text-gray-400 mb-2">
                        活跃计划
                      </p>
                      <p className="text-2xl font-bold text-gray-900 dark:text-gray-100">
                        {activePlans}
                      </p>
                    </div>
                    <div className="rounded-xl bg-gradient-to-br from-accent-500/10 to-accent-500/5 p-2.5">
                      <Sparkles className="h-5 w-5 text-accent-500" />
                    </div>
                  </div>
                </CardContent>
              </Card>

              {/* 今日事件 */}
              <Card>
                <CardContent className="p-5">
                  <div className="flex items-start justify-between">
                    <div>
                      <p className="text-xs font-medium text-gray-500 dark:text-gray-400 mb-2">
                        今日事件
                      </p>
                      <p className="text-2xl font-bold text-gray-900 dark:text-gray-100">
                        {todayEvents.length}
                      </p>
                    </div>
                    <div className="rounded-xl bg-gradient-to-br from-blue-500/10 to-blue-500/5 p-2.5">
                      <Calendar className="h-5 w-5 text-blue-500" />
                    </div>
                  </div>
                </CardContent>
              </Card>
            </div>
          )}

          {/* 今日事件列表 + AI 建议 */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            {/* 今日事件 */}
            <Card>
              <CardContent className="p-5">
                <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-4">
                  今日事件
                </h3>
                {todayEvents.length === 0 ? (
                  <div className="py-6 text-center">
                    <Calendar className="mx-auto mb-2 h-8 w-8 text-gray-300" />
                    <p className="text-sm text-gray-500">今日暂无事件</p>
                  </div>
                ) : (
                  <div className="space-y-2">
                    {todayEvents.map((event: any, idx: number) => (
                      <div key={idx} className="flex items-center gap-3">
                        <div className="flex-1 min-w-0">
                          <p className="text-sm font-medium text-gray-900 dark:text-gray-100 truncate">
                            {event.title}
                          </p>
                          <p className="text-xs text-gray-400 dark:text-gray-600">
                            {formatTime(event.startTime)} - {formatTime(event.endTime)}
                          </p>
                        </div>
                        {event.completed && (
                          <CheckCircle2 className="h-4 w-4 text-emerald-500" />
                        )}
                      </div>
                    ))}
                  </div>
                )}
              </CardContent>
            </Card>

            {/* AI 建议 */}
            <Card className="bg-gradient-to-br from-brand-500/5 to-accent-500/5">
              <CardContent className="p-5">
                <div className="flex items-center gap-2 mb-3">
                  <div className="rounded-lg bg-gradient-to-br from-brand-500 to-accent-500 p-1.5">
                    <Sparkles className="h-4 w-4 text-white" />
                  </div>
                  <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100">
                    AI 建议
                  </h3>
                </div>
                {isRecommendationLoading ? (
                  <div className="flex items-center gap-2 py-4">
                    <Loader2 className="h-4 w-4 text-brand-500 animate-spin" />
                    <span className="text-sm text-gray-500">生成中...</span>
                  </div>
                ) : aiRecommendation ? (
                  <p className="text-sm text-gray-700 dark:text-gray-300 leading-relaxed">
                    {(aiRecommendation as any)?.suggestion || String(aiRecommendation)}
                  </p>
                ) : (
                  <p className="text-sm text-gray-400">暂无 AI 建议</p>
                )}
              </CardContent>
            </Card>
          </div>
        </main>
      </div>
    </div>
  );
}
