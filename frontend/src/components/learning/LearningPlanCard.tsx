"use client";

import * as React from "react";
import { LearningPlan } from "@/lib/types";
import { Card, CardContent } from "@/components/ui/Card";
import { Progress } from "@/components/ui/Progress";
import { Badge } from "@/components/ui/Badge";
import { Clock, CalendarDays } from "lucide-react";

interface LearningPlanCardProps {
  plan: LearningPlan;
  onSelect?: (plan: LearningPlan) => void;
}

export function LearningPlanCard({ plan, onSelect }: LearningPlanCardProps) {
  const progress = Math.round(
    (plan.completedSessions / Math.max(plan.totalSessions, 1)) * 100
  );

  return (
    <Card
      className="cursor-pointer hover:shadow-md transition-all duration-200 hover:-translate-y-0.5"
      onClick={() => onSelect?.(plan)}
    >
      <CardContent className="p-5">
        <div className="flex items-start justify-between mb-3">
          <div className="flex-1">
            <h3 className="font-semibold text-gray-900 dark:text-white">
              {plan.title}
            </h3>
            <p className="text-sm text-gray-500 dark:text-gray-400 mt-1 line-clamp-2">
              {plan.description}
            </p>
          </div>
          <Badge variant="default" className="shrink-0 ml-2">
            {progress}%
          </Badge>
        </div>

        <Progress value={progress} className="mb-3" />

        <div className="flex items-center justify-between text-xs text-gray-500 dark:text-gray-400">
          <div className="flex items-center gap-1">
            <Clock className="h-3.5 w-3.5" />
            <span>
              {plan.completedSessions} / {plan.totalSessions} 课时
            </span>
          </div>
          <div className="flex items-center gap-1">
            <CalendarDays className="h-3.5 w-3.5" />
            <span>{plan.deadline}</span>
          </div>
        </div>

        <div className="flex flex-wrap gap-1.5 mt-3">
          {plan.tags.map((tag) => (
            <Badge key={tag} variant="secondary" className="text-[10px]">
              {tag}
            </Badge>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}