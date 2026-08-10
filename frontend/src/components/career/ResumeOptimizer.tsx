"use client";

import * as React from "react";
import { Card, CardContent } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Sparkles, Wand2, AlertCircle, FileText } from "lucide-react";
import { useCareer } from "@/hooks/useCareer";
import { useCareerStore } from "@/store/careerStore";

export function ResumeOptimizer() {
  const [jdText, setJdText] = React.useState("");
  const { optimizeResumeMutation } = useCareer();
  const resumes = useCareerStore((s) => s.resumes);
  const currentResumeId = useCareerStore((s) => s.currentResumeId);

  const handleOptimize = () => {
    optimizeResumeMutation.mutate(jdText);
  };

  const result = optimizeResumeMutation.data as any;
  const optimizedContent =
    result?.optimizedContent ||
    result?.content ||
    result?.result ||
    result?.suggestion ||
    (typeof result === "string" ? result : null);

  const suggestions: string[] = result?.suggestions ?? result?.recommendations ?? [];

  return (
    <Card>
      <CardContent className="p-6">
        <div className="flex items-center gap-3 mb-4">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-brand-500 to-accent-500">
            <Wand2 className="h-5 w-5 text-white" />
          </div>
          <div>
            <h3 className="font-semibold text-gray-900 dark:text-white">简历优化</h3>
            <p className="text-xs text-gray-500 dark:text-gray-400">AI 智能优化你的简历</p>
          </div>
        </div>

        {resumes.length > 0 && (
          <div className="mb-4">
            <p className="text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">选择简历</p>
            <div className="space-y-2">
              {resumes.map((resume) => (
                <div key={resume.id} className={`flex items-center gap-3 p-3 rounded-xl border cursor-pointer transition-all ${currentResumeId === resume.id ? "border-brand-500 bg-brand-50 dark:bg-brand-900/20" : "border-gray-200 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-800"}`}>
                  <FileText className="h-5 w-5 text-brand-500" />
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium truncate">{resume.name}</p>
                    {resume.parsedData && (
                      <p className="text-xs text-gray-500 truncate">{resume.parsedData.skills.slice(0, 3).join(", ")}</p>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        <div className="mb-4">
          <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1.5">
            目标职位描述（可选）
          </label>
          <textarea
            value={jdText}
            onChange={(e) => setJdText(e.target.value)}
            placeholder="粘贴目标职位的 JD，AI 会据此优化简历..."
            rows={4}
            className="w-full rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-4 py-3 text-sm resize-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500"
          />
        </div>

        <Button onClick={handleOptimize} disabled={optimizeResumeMutation.isPending} className="w-full">
          {optimizeResumeMutation.isPending ? <Sparkles className="mr-2 h-4 w-4 animate-spin" /> : <Sparkles className="mr-2 h-4 w-4" />}
          {optimizeResumeMutation.isPending ? "AI 优化中..." : "一键优化简历"}
        </Button>

        {optimizeResumeMutation.isError && (
          <div className="mt-4 p-4 rounded-xl bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 flex items-start gap-2">
            <AlertCircle className="h-4 w-4 text-red-500 shrink-0 mt-0.5" />
            <p className="text-sm text-red-600 dark:text-red-400">
              优化失败：{(optimizeResumeMutation.error as Error)?.message || "请稍后重试"}
            </p>
          </div>
        )}

        {optimizedContent && !optimizeResumeMutation.isError && (
          <div className="mt-4 p-4 rounded-xl bg-gradient-to-br from-brand-50 to-accent-50 dark:from-brand-950/30 dark:to-accent-950/30 border border-brand-200 dark:border-brand-800 animate-fade-in">
            <pre className="text-sm text-gray-700 dark:text-gray-300 whitespace-pre-wrap font-sans">{optimizedContent}</pre>
          </div>
        )}

        {suggestions.length > 0 && !optimizeResumeMutation.isError && (
          <div className="mt-4 space-y-2">
            <h4 className="text-sm font-semibold text-gray-900 dark:text-white">优化建议</h4>
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
      </CardContent>
    </Card>
  );
}
