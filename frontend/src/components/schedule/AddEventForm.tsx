"use client";

import * as React from "react";
import { Card, CardContent } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Plus } from "lucide-react";
import { useScheduleStore } from "@/store/scheduleStore";
import { ScheduleEvent } from "@/lib/types";

export function AddEventForm() {
  const [title, setTitle] = React.useState("");
  const [description, setDescription] = React.useState("");
  const [startTime, setStartTime] = React.useState("09:00");
  const [endTime, setEndTime] = React.useState("10:00");
  const [priority, setPriority] = React.useState<"low" | "medium" | "high">("medium");
  const [category, setCategory] = React.useState("学习");
  const addEvent = useScheduleStore((s) => s.addEvent);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim()) return;

    const today = new Date();
    const [sh, sm] = startTime.split(":").map(Number);
    const [eh, em] = endTime.split(":").map(Number);

    const newEvent: ScheduleEvent = {
      id: Math.random().toString(36).substring(2, 9),
      title,
      description,
      startTime: new Date(today.getFullYear(), today.getMonth(), today.getDate(), sh, sm).toISOString(),
      endTime: new Date(today.getFullYear(), today.getMonth(), today.getDate(), eh, em).toISOString(),
      completed: false,
      priority,
      category,
    };

    addEvent(newEvent);
    setTitle("");
    setDescription("");
    setStartTime("09:00");
    setEndTime("10:00");
  };

  return (
    <Card>
      <CardContent className="p-6">
        <h3 className="font-semibold text-gray-900 dark:text-white mb-4">添加日程</h3>
        <form onSubmit={handleSubmit} className="space-y-4">
          <Input
            label="标题"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="日程标题"
          />
          <div>
            <label className="text-sm font-medium text-gray-700 dark:text-gray-300 mb-1.5 block">描述</label>
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="日程描述（可选）"
              rows={2}
              className="w-full rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 py-2 text-sm resize-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500"
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <Input
              label="开始时间"
              type="time"
              value={startTime}
              onChange={(e) => setStartTime(e.target.value)}
            />
            <Input
              label="结束时间"
              type="time"
              value={endTime}
              onChange={(e) => setEndTime(e.target.value)}
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="text-sm font-medium text-gray-700 dark:text-gray-300 mb-1.5 block">优先级</label>
              <select
                value={priority}
                onChange={(e) => setPriority(e.target.value as "low" | "medium" | "high")}
                className="w-full rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500"
              >
                <option value="low">低</option>
                <option value="medium">中</option>
                <option value="high">高</option>
              </select>
            </div>
            <div>
              <label className="text-sm font-medium text-gray-700 dark:text-gray-300 mb-1.5 block">分类</label>
              <select
                value={category}
                onChange={(e) => setCategory(e.target.value)}
                className="w-full rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500"
              >
                <option value="学习">学习</option>
                <option value="工作">工作</option>
                <option value="健康">健康</option>
                <option value="求职">求职</option>
                <option value="阅读">阅读</option>
                <option value="其他">其他</option>
              </select>
            </div>
          </div>
          <Button type="submit" className="w-full">
            <Plus className="mr-2 h-4 w-4" />
            添加日程
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}
