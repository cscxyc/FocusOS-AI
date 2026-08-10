"use client";

import * as React from "react";
import { Card, CardContent } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import { Target, Sparkles, CheckCircle2, XCircle, AlertCircle } from "lucide-react";
import { useCareer } from "@/hooks/useCareer";

export function JDAnalyzer() {
  const [jdText, setJdText] = React.useState("");
  const { analyzeJDMutation } = useCareer();

  const analysis = analyzeJDMutation.data as any;

  const handleAnalyze = () => {
    if (!jdText.trim()) return;
    analyzeJDMutation.mutate(jdText);
  };

  const matchScore = analysis?.matchScore ?? analysis?.matchRate;
  const matchingSkills: string[] = analysis?.matchingSkills ?? analysis?.matchedSkills ?? [];
  const missingSkills: string[] = analysis?.missingSkills ?? analysis?.gaps ?? [];
  const suggestions: string[] = analysis?.suggestions ?? analysis?.recommendations ?? [];

  return (
    <Card>
      <CardContent className="p-6">
        <div className="flex items-center gap-3 mb-4">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-brand-500 to-accent-500">
            <Target className="h-5 w-5 text-white" />
          </div>
          <div>
            <h3 className="font-semibold text-gray-900 dark:text-white">JD 分析器</h3>
            <p className="text-xs text-gray-500 dark:text-gray-400">粘贴职位描述，AI 自动分析匹配度</p>
          </div>
        </div>

        <textarea
          value={jdText}
          onChange={(e) => setJdText(e.target.value)}
          placeholder="粘贴职位描述 (JD) 到此处..."
          rows={6}
          className="w-full rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-4 py-3 text-sm resize-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500"
        />

        <Button onClick={handleAnalyze} disabled={analyzeJDMutation.isPending || !jdText.trim()} className="w-full mt-4">
          {analyzeJDMutation.isPending ? <Sparkles className="mr-2 h-4 w-4 animate-spin" /> : <Sparkles className="mr-2 h-4 w-4" />}
          {analyzeJDMutation.isPending ? "AI 分析中..." : "开始分析"}
        </Button>

        {analyzeJDMutation.isError && (
          <div className="mt-4 p-4 rounded-xl bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 flex items-start gap-2">
            <AlertCircle className="h-4 w-4 text-red-500 shrink-0 mt-0.5" />
            <p className="text-sm text-red-600 dark:text-red-400">
              分析失败：{(analyzeJDMutation.error as Error)?.message || "请稍后重试"}
            </p>
          </div>
        )}

        {analysis && !analyzeJDMutation.isError && (
          <div className="mt-6 space-y-4 animate-fade-in">
            {typeof matchScore === "number" && (
              <div className="flex items-center justify-center p-6 rounded-xl bg-gradient-to-br from-brand-50 to-accent-50 dark:from-brand-950/30 dark:to-accent-950/30">
                <div className="text-center">
                  <span className="text-5xl font-bold bg-gradient-to-r from-brand-600 to-accent-600 bg-clip-text text-transparent">{matchScore}%</span>
                  <p className="text-sm text-gray-600 dark:text-gray-400 mt-1">匹配度</p>
                </div>
              </div>
            )}

            {matchingSkills.length > 0 && (
              <div>
                <h4 className="text-sm font-semibold text-gray-900 dark:text-white mb-2 flex items-center gap-2">
                  <CheckCircle2 className="h-4 w-4 text-green-500" />匹配技能
                </h4>
                <div className="flex flex-wrap gap-2">
                  {matchingSkills.map((skill) => (<Badge key={skill} variant="success">{skill}</Badge>))}
                </div>
              </div>
            )}

            {missingSkills.length > 0 && (
              <div>
                <h4 className="text-sm font-semibold text-gray-900 dark:text-white mb-2 flex items-center gap-2">
                  <XCircle className="h-4 w-4 text-red-500" />缺失技能
                </h4>
                <div className="flex flex-wrap gap-2">
                  {missingSkills.map((skill) => (<Badge key={skill} variant="destructive">{skill}</Badge>))}
                </div>
              </div>
            )}

            {suggestions.length > 0 && (
              <div>
                <h4 className="text-sm font-semibold text-gray-900 dark:text-white mb-2">改进建议</h4>
                <ul className="space-y-2">
                  {suggestions.map((suggestion, idx) => (
                    <li key={idx} className="flex items-start gap-2 text-sm text-gray-700 dark:text-gray-300">
                      <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-brand-100 dark:bg-brand-900/30 text-brand-600 dark:text-brand-400 text-xs font-bold">{idx + 1}</span>
                      {suggestion}
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
