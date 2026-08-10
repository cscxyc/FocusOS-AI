"use client";

import * as React from "react";
import { Card, CardContent } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { ResumeScoreCard } from "./ResumeScoreCard";
import {
  CheckCircle2,
  XCircle,
  AlertTriangle,
  Lightbulb,
  ArrowRight,
  KeyRound,
  ThumbsUp,
  ThumbsDown,
  Target,
} from "lucide-react";
import type { ResumeEvaluation as ResumeEvaluationData } from "@/lib/types";

interface ResumeEvaluationProps {
  evaluation: ResumeEvaluationData;
  jobTitle?: string;
  company?: string;
}

/**
 * Sprint 8-A: 简历评估结果完整展示
 * <p>
 * 展示：
 * 1. 总体评分（ResumeScoreCard）
 * 2. 优势
 * 3. 不足
 * 4. 关键词匹配明细（MATCH / MISSING）
 * 5. 优化建议
 * 6. 下一步行动
 */
export function ResumeEvaluation({ evaluation, jobTitle, company }: ResumeEvaluationProps) {
  const {
    strengths = [],
    weaknesses = [],
    missingKeywords = [],
    keywordMatches = [],
    suggestions = [],
    recommendedActions = [],
  } = evaluation;

  return (
    <div className="space-y-4">
      {/* 岗位信息 */}
      {(jobTitle || company) && (
        <Card>
          <CardContent className="p-4 flex items-center gap-2">
            <Target className="h-4 w-4 text-brand-500 flex-shrink-0" />
            <span className="text-sm text-gray-500 dark:text-gray-400">评估目标：</span>
            <span className="text-sm font-semibold text-gray-900 dark:text-white">
              {jobTitle || "未指定岗位"}
            </span>
            {company && (
              <Badge variant="secondary" className="ml-1">
                {company}
              </Badge>
            )}
          </CardContent>
        </Card>
      )}

      {/* 评分卡（含雷达图） */}
      <ResumeScoreCard evaluation={evaluation} />

      {/* 优势 */}
      {strengths.length > 0 && (
        <Card>
          <CardContent className="p-5">
            <div className="flex items-center gap-2 mb-3">
              <div className="h-7 w-7 rounded-lg bg-green-100 dark:bg-green-900/30 flex items-center justify-center">
                <ThumbsUp className="h-4 w-4 text-green-600 dark:text-green-400" />
              </div>
              <h3 className="text-sm font-semibold text-gray-900 dark:text-white">
                优势
                <span className="ml-1.5 text-xs text-gray-400">({strengths.length})</span>
              </h3>
            </div>
            <ul className="space-y-2">
              {strengths.map((s, i) => (
                <li key={i} className="flex items-start gap-2 text-sm text-gray-700 dark:text-gray-300">
                  <CheckCircle2 className="h-4 w-4 text-green-500 flex-shrink-0 mt-0.5" />
                  <span>{s}</span>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      )}

      {/* 不足 */}
      {weaknesses.length > 0 && (
        <Card>
          <CardContent className="p-5">
            <div className="flex items-center gap-2 mb-3">
              <div className="h-7 w-7 rounded-lg bg-red-100 dark:bg-red-900/30 flex items-center justify-center">
                <ThumbsDown className="h-4 w-4 text-red-600 dark:text-red-400" />
              </div>
              <h3 className="text-sm font-semibold text-gray-900 dark:text-white">
                不足
                <span className="ml-1.5 text-xs text-gray-400">({weaknesses.length})</span>
              </h3>
            </div>
            <ul className="space-y-2">
              {weaknesses.map((w, i) => (
                <li key={i} className="flex items-start gap-2 text-sm text-gray-700 dark:text-gray-300">
                  <XCircle className="h-4 w-4 text-red-500 flex-shrink-0 mt-0.5" />
                  <span>{w}</span>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      )}

      {/* 关键词匹配明细 */}
      {keywordMatches.length > 0 && (
        <Card>
          <CardContent className="p-5">
            <div className="flex items-center gap-2 mb-3">
              <div className="h-7 w-7 rounded-lg bg-brand-100 dark:bg-brand-900/30 flex items-center justify-center">
                <KeyRound className="h-4 w-4 text-brand-600 dark:text-brand-400" />
              </div>
              <h3 className="text-sm font-semibold text-gray-900 dark:text-white">
                关键词匹配
                <span className="ml-1.5 text-xs text-gray-400">
                  ({keywordMatches.filter((k) => k.status === "MATCH").length}/{keywordMatches.length})
                </span>
              </h3>
            </div>
            <div className="space-y-2">
              {keywordMatches.map((k, i) => (
                <div
                  key={i}
                  className="flex items-start gap-2 p-2 rounded-lg bg-gray-50 dark:bg-gray-800/50"
                >
                  {k.status === "MATCH" ? (
                    <CheckCircle2 className="h-4 w-4 text-green-500 flex-shrink-0 mt-0.5" />
                  ) : (
                    <XCircle className="h-4 w-4 text-red-500 flex-shrink-0 mt-0.5" />
                  )}
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-medium text-gray-900 dark:text-white">
                        {k.keyword}
                      </span>
                      <Badge
                        variant={k.status === "MATCH" ? "success" : "destructive"}
                        className="text-[10px]"
                      >
                        {k.status === "MATCH" ? "匹配" : "缺失"}
                      </Badge>
                    </div>
                    {k.evidence && (
                      <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">
                        {k.evidence}
                      </p>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}

      {/* 缺失关键词（汇总） */}
      {missingKeywords.length > 0 && (
        <Card>
          <CardContent className="p-5">
            <div className="flex items-center gap-2 mb-3">
              <div className="h-7 w-7 rounded-lg bg-orange-100 dark:bg-orange-900/30 flex items-center justify-center">
                <AlertTriangle className="h-4 w-4 text-orange-600 dark:text-orange-400" />
              </div>
              <h3 className="text-sm font-semibold text-gray-900 dark:text-white">
                缺失关键词
                <span className="ml-1.5 text-xs text-gray-400">({missingKeywords.length})</span>
              </h3>
            </div>
            <div className="flex flex-wrap gap-2">
              {missingKeywords.map((k, i) => (
                <Badge key={i} variant="destructive" className="text-xs">
                  {k}
                </Badge>
              ))}
            </div>
          </CardContent>
        </Card>
      )}

      {/* 优化建议 */}
      {suggestions.length > 0 && (
        <Card>
          <CardContent className="p-5">
            <div className="flex items-center gap-2 mb-3">
              <div className="h-7 w-7 rounded-lg bg-blue-100 dark:bg-blue-900/30 flex items-center justify-center">
                <Lightbulb className="h-4 w-4 text-blue-600 dark:text-blue-400" />
              </div>
              <h3 className="text-sm font-semibold text-gray-900 dark:text-white">
                优化建议
                <span className="ml-1.5 text-xs text-gray-400">({suggestions.length})</span>
              </h3>
            </div>
            <ul className="space-y-2">
              {suggestions.map((s, i) => (
                <li key={i} className="flex items-start gap-2 text-sm text-gray-700 dark:text-gray-300">
                  <span className="flex-shrink-0 w-5 h-5 rounded-full bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 text-xs font-bold flex items-center justify-center mt-0.5">
                    {i + 1}
                  </span>
                  <span>{s}</span>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      )}

      {/* 下一步行动 */}
      {recommendedActions.length > 0 && (
        <Card>
          <CardContent className="p-5">
            <div className="flex items-center gap-2 mb-3">
              <div className="h-7 w-7 rounded-lg bg-purple-100 dark:bg-purple-900/30 flex items-center justify-center">
                <ArrowRight className="h-4 w-4 text-purple-600 dark:text-purple-400" />
              </div>
              <h3 className="text-sm font-semibold text-gray-900 dark:text-white">
                下一步行动
                <span className="ml-1.5 text-xs text-gray-400">({recommendedActions.length})</span>
              </h3>
            </div>
            <ul className="space-y-2">
              {recommendedActions.map((a, i) => (
                <li key={i} className="flex items-start gap-2 text-sm text-gray-700 dark:text-gray-300">
                  <ArrowRight className="h-4 w-4 text-purple-500 flex-shrink-0 mt-0.5" />
                  <span>{a}</span>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
