"use client";

import * as React from "react";
import { Card, CardContent } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import { Sparkles, Smile, Frown, Meh, Send, AlertCircle } from "lucide-react";
import { format } from "date-fns";
import { zhCN } from "date-fns/locale";
import { useLearning } from "@/hooks/useLearning";

type Mood = "great" | "good" | "neutral" | "bad";

const moodIcons: Record<Mood, typeof Smile> = {
  great: Smile,
  good: Smile,
  neutral: Meh,
  bad: Frown,
};

const moodColors: Record<Mood, string> = {
  great: "text-green-500",
  good: "text-brand-500",
  neutral: "text-amber-500",
  bad: "text-red-500",
};

export function DailyReview() {
  const [achievements, setAchievements] = React.useState("");
  const [challenges, setChallenges] = React.useState("");
  const [mood, setMood] = React.useState<Mood>("good");
  const { dailyReviewMutation } = useLearning();

  const handleAnalyze = () => {
    dailyReviewMutation.mutate({ achievements, challenges, mood });
  };

  const result = dailyReviewMutation.data as any;
  const aiSummary =
    result?.aiSummary ||
    result?.summary ||
    result?.content ||
    result?.message ||
    (typeof result === "string" ? result : null);

  const moods: Mood[] = ["great", "good", "neutral", "bad"];

  return (
    <Card>
      <CardContent className="p-6">
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-brand-500 to-accent-500">
              <Sparkles className="h-5 w-5 text-white" />
            </div>
            <div>
              <h3 className="font-semibold text-gray-900 dark:text-white">每日复盘</h3>
              <p className="text-xs text-gray-500 dark:text-gray-400">
                {format(new Date(), "yyyy年MM月dd日", { locale: zhCN })}
              </p>
            </div>
          </div>
          <Badge variant="warning">AI 驱动</Badge>
        </div>

        <div className="space-y-4">
          <div>
            <label className="text-sm font-medium text-gray-700 dark:text-gray-300 mb-1.5 block">
              今日成就
            </label>
            <textarea
              value={achievements}
              onChange={(e) => setAchievements(e.target.value)}
              placeholder="今天完成了哪些事情？"
              className="flex h-20 w-full rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 py-2 text-sm resize-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500"
            />
          </div>

          <div>
            <label className="text-sm font-medium text-gray-700 dark:text-gray-300 mb-1.5 block">
              遇到的挑战
            </label>
            <textarea
              value={challenges}
              onChange={(e) => setChallenges(e.target.value)}
              placeholder="遇到了什么困难？如何解决的？"
              className="flex h-20 w-full rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 py-2 text-sm resize-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500"
            />
          </div>

          <div>
            <label className="text-sm font-medium text-gray-700 dark:text-gray-300 mb-2 block">
              今日心情
            </label>
            <div className="flex gap-2">
              {moods.map((m) => {
                const Icon = moodIcons[m];
                return (
                  <button
                    key={m}
                    onClick={() => setMood(m)}
                    className={`flex-1 flex flex-col items-center gap-1 p-3 rounded-xl border-2 transition-all ${
                      mood === m
                        ? "border-brand-500 bg-brand-50 dark:bg-brand-900/30"
                        : "border-gray-200 dark:border-gray-700 hover:border-brand-300"
                    }`}
                  >
                    <Icon className={`h-6 w-6 ${moodColors[m]}`} />
                  </button>
                );
              })}
            </div>
          </div>

          {dailyReviewMutation.isError && (
            <div className="p-4 rounded-xl bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 flex items-start gap-2">
              <AlertCircle className="h-4 w-4 text-red-500 shrink-0 mt-0.5" />
              <p className="text-sm text-red-600 dark:text-red-400">
                生成失败：{(dailyReviewMutation.error as Error)?.message || "请稍后重试"}
              </p>
            </div>
          )}

          {aiSummary && !dailyReviewMutation.isError && (
            <div className="p-4 rounded-xl bg-gradient-to-br from-brand-50 to-accent-50 dark:from-brand-950/30 dark:to-accent-950/30 border border-brand-200 dark:border-brand-800">
              <p className="text-sm text-gray-700 dark:text-gray-300">{aiSummary}</p>
            </div>
          )}

          <Button onClick={handleAnalyze} disabled={dailyReviewMutation.isPending} className="w-full">
            {dailyReviewMutation.isPending ? (
              <Sparkles className="mr-2 h-4 w-4 animate-spin" />
            ) : (
              <Send className="mr-2 h-4 w-4" />
            )}
            {dailyReviewMutation.isPending ? "AI 分析中..." : "AI 生成复盘总结"}
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}
