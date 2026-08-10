"use client";

import * as React from "react";
import { Target } from "@/lib/types";
import { Card, CardContent } from "@/components/ui/Card";
import { Progress } from "@/components/ui/Progress";
import { cn } from "@/lib/utils";

interface TargetCardProps {
  target: Target;
}

const categoryIcons: Record<Target["category"], string> = {
  study: "📚",
  fitness: "💪",
  reading: "📖",
  career: "💼",
  investment: "📈",
  other: "🎯",
};

const categoryLabels: Record<Target["category"], string> = {
  study: "学习",
  fitness: "健身",
  reading: "阅读",
  career: "求职",
  investment: "投资",
  other: "其他",
};

export function TargetCard({ target }: TargetCardProps) {
  const percentage = Math.min(100, Math.round((target.progress / target.goal) * 100));
  const isComplete = percentage >= 100;

  return (
    <Card className="group hover:shadow-lg transition-all duration-300 hover:-translate-y-0.5">
      <CardContent className="p-4">
        <div className="flex items-start justify-between mb-3">
          <div className="flex items-center gap-2">
            <span className="text-2xl">{categoryIcons[target.category]}</span>
            <div>
              <h3 className="font-semibold text-gray-900 dark:text-white text-sm">
                {target.name}
              </h3>
              <p className="text-xs text-gray-500 dark:text-gray-400">
                {categoryLabels[target.category]}
              </p>
            </div>
          </div>
          <span
            className={cn(
              "text-xs font-bold px-2 py-1 rounded-lg",
              isComplete
                ? "bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400"
                : "bg-brand-100 text-brand-700 dark:bg-brand-900/30 dark:text-brand-400"
            )}
          >
            {percentage}%
          </span>
        </div>

        <Progress value={percentage} />

        <div className="flex items-center justify-between mt-2 text-xs text-gray-500 dark:text-gray-400">
          <span>
            {target.progress} / {target.goal} {target.unit}
          </span>
          {isComplete ? (
            <span className="text-green-500 font-medium">已完成</span>
          ) : (
            <span>还差 {target.goal - target.progress} {target.unit}</span>
          )}
        </div>
      </CardContent>
    </Card>
  );
}