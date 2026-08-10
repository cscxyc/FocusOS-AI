"use client";

import * as React from "react";
import { Sidebar } from "@/components/layout/Sidebar";
import { Header } from "@/components/layout/Header";
import { LearningPlanCard } from "@/components/learning/LearningPlanCard";
import { PomodoroTimer } from "@/components/learning/PomodoroTimer";
import { DailyReview } from "@/components/learning/DailyReview";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/Tabs";
import { Card, CardContent } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import { Input } from "@/components/ui/Input";
import { useLearning } from "@/hooks/useLearning";
import { useAuthStore } from "@/store/authStore";
import { BookOpen, Clock, RefreshCw, Plus, Loader2 } from "lucide-react";

export default function LearningPage() {
  const { plans, isLoading, createPlanMutation } = useLearning();
  const [activeTab, setActiveTab] = React.useState("plans");
  const [isCreateOpen, setIsCreateOpen] = React.useState(false);
  const user = useAuthStore((s) => s.user);

  const [form, setForm] = React.useState({
    title: "",
    goal: "",
    startDate: "",
    endDate: "",
    dailyTargetMinutes: 60,
  });

  const handleCreate = () => {
    if (!form.title.trim()) return;
    createPlanMutation.mutate(
      {
        title: form.title,
        goal: form.goal,
        startDate: form.startDate,
        endDate: form.endDate,
        dailyTargetMinutes: Number(form.dailyTargetMinutes) || 0,
      },
      {
        onSuccess: () => {
          setIsCreateOpen(false);
          setForm({
            title: "",
            goal: "",
            startDate: "",
            endDate: "",
            dailyTargetMinutes: 60,
          });
        },
      }
    );
  };

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-950">
      <Sidebar />
      <div className="lg:pl-64">
        <Header />
        <main className="p-6">
          <div className="flex items-center justify-between mb-6">
            <div>
              <h1 className="text-2xl font-bold text-gray-900 dark:text-white">学习中心</h1>
              <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
                管理学习计划、使用番茄钟、每日复盘
              </p>
            </div>
            <Button onClick={() => setIsCreateOpen(true)}>
              <Plus className="h-4 w-4 mr-2" />
              创建学习计划
            </Button>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            <div className="lg:col-span-2">
              <Tabs value={activeTab} onValueChange={setActiveTab} className="w-full">
                <TabsList>
                  <TabsTrigger value="plans">
                    <BookOpen className="h-4 w-4 mr-2" />学习计划
                  </TabsTrigger>
                  <TabsTrigger value="pomodoro">
                    <Clock className="h-4 w-4 mr-2" />番茄钟
                  </TabsTrigger>
                  <TabsTrigger value="review">
                    <RefreshCw className="h-4 w-4 mr-2" />学习复盘
                  </TabsTrigger>
                </TabsList>

                <TabsContent value="plans">
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mt-4">
                    {isLoading ? (
                      <div className="col-span-full flex items-center justify-center py-12 text-gray-500">
                        <Loader2 className="h-6 w-6 animate-spin mr-2" />
                        <span>加载中...</span>
                      </div>
                    ) : plans.length === 0 ? (
                      <div className="col-span-full text-center py-12 text-gray-500 dark:text-gray-400">
                        <BookOpen className="h-12 w-12 mx-auto mb-3 opacity-50" />
                        <p>还没有学习计划，点击下方按钮创建第一个计划 🚀</p>
                        <div className="mt-6">
                          <Button onClick={() => setIsCreateOpen(true)}>
                            <Plus className="h-4 w-4 mr-2" />
                            创建学习计划
                          </Button>
                        </div>
                      </div>
                    ) : (
                      plans.map((plan: any) => (
                        <LearningPlanCard key={plan.id} plan={plan} />
                      ))
                    )}
                  </div>
                </TabsContent>

                <TabsContent value="pomodoro">
                  <div className="mt-4">
                    <PomodoroTimer />
                  </div>
                </TabsContent>

                <TabsContent value="review">
                  <div className="mt-4">
                    <DailyReview />
                  </div>
                </TabsContent>
              </Tabs>
            </div>

            <div className="space-y-4">
              <Card>
                <CardContent className="p-4">
                  <h3 className="font-semibold text-sm mb-3">今日学习目标</h3>
                  <div className="space-y-2">
                    <div className="flex justify-between text-sm">
                      <span className="text-gray-500">坚持学习</span>
                      <span className="font-medium">{user?.username ? `${user.username} 加油` : "继续努力"}</span>
                    </div>
                    <div className="w-full bg-gray-200 dark:bg-gray-700 rounded-full h-2">
                      <div className="bg-brand-500 h-2 rounded-full" style={{ width: '0%' }}></div>
                    </div>
                  </div>
                </CardContent>
              </Card>
              <Card>
                <CardContent className="p-4">
                  <h3 className="font-semibold text-sm mb-3">学习计划</h3>
                  <div className="flex items-baseline gap-2">
                    <span className="text-3xl font-bold text-brand-500">{plans.length}</span>
                    <span className="text-sm text-gray-500">个进行中</span>
                  </div>
                  <p className="text-xs text-gray-500 mt-1">保持学习习惯</p>
                </CardContent>
              </Card>
            </div>
          </div>
        </main>
      </div>

      <Modal
        isOpen={isCreateOpen}
        onClose={() => setIsCreateOpen(false)}
        title="创建学习计划"
        size="lg"
        footer={
          <>
            <Button variant="outline" onClick={() => setIsCreateOpen(false)}>
              取消
            </Button>
            <Button
              onClick={handleCreate}
              disabled={createPlanMutation.isPending || !form.title.trim()}
            >
              {createPlanMutation.isPending ? "创建中..." : "创建计划"}
            </Button>
          </>
        }
      >
        <div className="space-y-4">
          <Input
            label="计划标题"
            placeholder="例如：系统设计入门"
            value={form.title}
            onChange={(e) => setForm({ ...form, title: e.target.value })}
          />
          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1.5">
              学习目标
            </label>
            <textarea
              placeholder="描述你的学习目标"
              value={form.goal}
              onChange={(e) => setForm({ ...form, goal: e.target.value })}
              className="flex h-20 w-full rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 py-2 text-sm resize-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500"
            />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <Input
              label="开始日期"
              type="date"
              value={form.startDate}
              onChange={(e) => setForm({ ...form, startDate: e.target.value })}
            />
            <Input
              label="结束日期"
              type="date"
              value={form.endDate}
              onChange={(e) => setForm({ ...form, endDate: e.target.value })}
            />
          </div>
          <Input
            label="每日目标时长（分钟）"
            type="number"
            value={form.dailyTargetMinutes}
            onChange={(e) =>
              setForm({ ...form, dailyTargetMinutes: Number(e.target.value) })
            }
          />
        </div>
      </Modal>
    </div>
  );
}
