"use client";

import * as React from "react";
import { Card, CardContent } from "@/components/ui/Card";
import { Sparkles, TrendingUp, Brain, Lightbulb } from "lucide-react";
import { cn } from "@/lib/utils";

interface AISuggestionProps {
  suggestion?: string | null;
}

export function AISuggestion({ suggestion }: AISuggestionProps) {
  const insights = [
    { icon: TrendingUp, label: "本周进步", value: "+23%", color: "text-green-500" },
    { icon: Brain, label: "专注时长", value: "4.2h", color: "text-brand-500" },
    { icon: Lightbulb, label: "效率指数", value: "85", color: "text-accent-500" },
  ];

  return (
    <Card className="relative overflow-hidden border-brand-200 dark:border-brand-800 bg-gradient-to-br from-brand-50 via-white to-accent-50 dark:from-brand-950/30 dark:via-gray-900 dark:to-accent-950/30">
      <div className="absolute top-0 right-0 w-40 h-40 bg-gradient-to-br from-brand-400/20 to-accent-400/20 rounded-full blur-3xl -translate-y-1/2 translate-x-1/2" />
      <CardContent className="relative p-6">
        <div className="flex items-center gap-3 mb-4">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-brand-500 to-accent-500">
            <Sparkles className="h-5 w-5 text-white" />
          </div>
          <div>
            <h3 className="font-semibold text-gray-900 dark:text-white">
              AI 智能建议
            </h3>
            <p className="text-xs text-gray-500 dark:text-gray-400">
              基于你的数据分析
            </p>
          </div>
        </div>

        <p className="text-sm text-gray-700 dark:text-gray-300 leading-relaxed mb-5">
          {suggestion || "继续保持当前的学习节奏，你在深度学习方面表现出色。建议明天安排更多的实践练习时间。"}
        </p>

        <div className="grid grid-cols-3 gap-4">
          {insights.map((item) => (
            <div
              key={item.label}
              className="flex flex-col items-center p-3 rounded-xl bg-white/60 dark:bg-gray-800/60 backdrop-blur-sm"
            >
              <item.icon className={cn("h-5 w-5 mb-1", item.color)} />
              <span className="text-lg font-bold text-gray-900 dark:text-white">
                {item.value}
              </span>
              <span className="text-xs text-gray-500 dark:text-gray-400">
                {item.label}
              </span>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}