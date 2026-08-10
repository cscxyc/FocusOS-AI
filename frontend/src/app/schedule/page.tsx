"use client";

import * as React from "react";
import { Sidebar } from "@/components/layout/Sidebar";
import { Header } from "@/components/layout/Header";
import { EventTimeline } from "@/components/schedule/EventTimeline";
import { AddEventForm } from "@/components/schedule/AddEventForm";
import { Card, CardContent } from "@/components/ui/Card";
import { useScheduleStore } from "@/store/scheduleStore";
import { Calendar, CheckCircle2, Clock } from "lucide-react";
import { format, formatDistanceToNow } from "date-fns";
import { zhCN } from "date-fns/locale";

export default function SchedulePage() {
  const events = useScheduleStore((s) => s.events);
  const completedCount = events.filter((e) => e.completed).length;
  const today = new Date();

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-950">
      <Sidebar />
      <div className="lg:pl-64">
        <Header />
        <main className="p-6">
          <div className="flex items-center justify-between mb-6">
            <div>
              <h1 className="text-2xl font-bold text-gray-900 dark:text-white">日程管理</h1>
              <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
                {format(today, "yyyy年MM月dd日 EEEE", { locale: zhCN })}
              </p>
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
            <Card>
              <CardContent className="p-4 flex items-center gap-3">
                <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-brand-100 dark:bg-brand-900/30">
                  <Calendar className="h-5 w-5 text-brand-500" />
                </div>
                <div>
                  <p className="text-2xl font-bold text-gray-900 dark:text-white">{events.length}</p>
                  <p className="text-xs text-gray-500">今日日程</p>
                </div>
              </CardContent>
            </Card>
            <Card>
              <CardContent className="p-4 flex items-center gap-3">
                <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-green-100 dark:bg-green-900/30">
                  <CheckCircle2 className="h-5 w-5 text-green-500" />
                </div>
                <div>
                  <p className="text-2xl font-bold text-gray-900 dark:text-white">{completedCount}</p>
                  <p className="text-xs text-gray-500">已完成</p>
                </div>
              </CardContent>
            </Card>
            <Card>
              <CardContent className="p-4 flex items-center gap-3">
                <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-amber-100 dark:bg-amber-900/30">
                  <Clock className="h-5 w-5 text-amber-500" />
                </div>
                <div>
                  <p className="text-2xl font-bold text-gray-900 dark:text-white">{events.length - completedCount}</p>
                  <p className="text-xs text-gray-500">待完成</p>
                </div>
              </CardContent>
            </Card>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            <div className="lg:col-span-2">
              <Card>
                <CardContent className="p-6">
                  <h3 className="font-semibold text-gray-900 dark:text-white mb-6">
                    今日时间线
                  </h3>
                  <EventTimeline />
                </CardContent>
              </Card>
            </div>
            <div>
              <AddEventForm />
            </div>
          </div>
        </main>
      </div>
    </div>
  );
}
