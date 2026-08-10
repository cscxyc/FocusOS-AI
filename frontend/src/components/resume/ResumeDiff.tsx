"use client";

import * as React from "react";
import { Card, CardContent } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { useResumeDiff, useResumeVersions } from "@/hooks/useResume";
import {
  GitCompare,
  Plus,
  Minus,
  Equal,
  Loader2,
  AlertCircle,
  X,
  ArrowRight,
  TrendingUp,
} from "lucide-react";
import type { ResumeDiffResponse } from "@/lib/types";

interface ResumeDiffProps {
  versionAId: number;
  versionBId: number;
  onClose?: () => void;
}

/**
 * Sprint 7-C-B: 简历版本 Diff 对比组件
 * 左右对比两个简历版本，展示技能/内容差异
 */
export function ResumeDiff({ versionAId, versionBId, onClose }: ResumeDiffProps) {
  const diffMutation = useResumeDiff();
  const versionsQuery = useResumeVersions();

  React.useEffect(() => {
    diffMutation.mutate({ versionA: versionAId, versionB: versionBId });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [versionAId, versionBId]);

  const versions = versionsQuery.data || [];
  const versionA = versions.find((v) => v.id === versionAId);
  const versionB = versions.find((v) => v.id === versionBId);

  if (diffMutation.isPending) {
    return (
      <Card>
        <CardContent className="p-8 text-center">
          <Loader2 className="h-8 w-8 mx-auto mb-3 animate-spin text-brand-500" />
          <p className="text-sm text-gray-500">正在对比简历版本...</p>
        </CardContent>
      </Card>
    );
  }

  if (diffMutation.isError) {
    return (
      <Card>
        <CardContent className="p-8 text-center">
          <AlertCircle className="h-12 w-12 mx-auto mb-3 text-red-500" />
          <p className="text-sm text-red-600 dark:text-red-400 mb-2">对比失败</p>
          <p className="text-xs text-gray-500 mb-4">
            {diffMutation.error instanceof Error
              ? diffMutation.error.message
              : "未知错误"}
          </p>
          {onClose && (
            <Button variant="outline" size="sm" onClick={onClose}>
              关闭
            </Button>
          )}
        </CardContent>
      </Card>
    );
  }

  const diff: ResumeDiffResponse | undefined = diffMutation.data;

  if (!diff) return null;

  return (
    <div className="space-y-4">
      {/* 顶部标题栏 */}
      <Card>
        <CardContent className="p-4">
          <div className="flex items-center justify-between gap-3 flex-wrap">
            <div className="flex items-center gap-3 flex-wrap">
              <div className="flex items-center gap-2">
                <GitCompare className="h-5 w-5 text-brand-500" />
                <h3 className="font-semibold text-gray-900 dark:text-white">简历版本对比</h3>
              </div>
              <div className="flex items-center gap-2 text-sm">
                <Badge variant="secondary">{versionA?.targetPosition || `#${versionAId}`}</Badge>
                <ArrowRight className="h-3.5 w-3.5 text-gray-400" />
                <Badge variant="default">{versionB?.targetPosition || `#${versionBId}`}</Badge>
              </div>
            </div>
            {onClose && (
              <Button variant="ghost" size="sm" onClick={onClose} className="gap-1">
                <X className="h-4 w-4" />
                关闭
              </Button>
            )}
          </div>

          {/* 相似度评分 */}
          {diff.summary && (
            <div className="mt-3 flex items-center gap-4 text-xs">
              <span className="flex items-center gap-1.5 text-gray-500">
                <TrendingUp className="h-3.5 w-3.5" />
                相似度
                <span className="font-semibold text-gray-900 dark:text-white">
                  {diff.summary.similarityScore}%
                </span>
              </span>
              <span className="text-green-600 dark:text-green-400 flex items-center gap-1">
                <Plus className="h-3 w-3" />
                A 独有 {diff.summary.addedCount}
              </span>
              <span className="text-red-600 dark:text-red-400 flex items-center gap-1">
                <Minus className="h-3 w-3" />
                B 独有 {diff.summary.removedCount}
              </span>
              <span className="text-gray-500 flex items-center gap-1">
                <Equal className="h-3 w-3" />
                共有 {diff.summary.commonCount}
              </span>
            </div>
          )}
        </CardContent>
      </Card>

      {/* 技能关键词对比（左右分栏） */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* A 独有 */}
        <Card className="border-green-200 dark:border-green-900">
          <CardContent className="p-4">
            <div className="flex items-center gap-1.5 mb-3">
              <Plus className="h-4 w-4 text-green-600" />
              <h4 className="text-sm font-semibold text-gray-900 dark:text-white">
                版本 A 独有 ({diff.added.length})
              </h4>
            </div>
            <div className="flex flex-wrap gap-1.5">
              {diff.added.length === 0 ? (
                <p className="text-xs text-gray-400">无</p>
              ) : (
                diff.added.map((skill) => (
                  <Badge
                    key={skill}
                    variant="success"
                    className="text-[11px] bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300"
                  >
                    {skill}
                  </Badge>
                ))
              )}
            </div>
          </CardContent>
        </Card>

        {/* 共有 */}
        <Card>
          <CardContent className="p-4">
            <div className="flex items-center gap-1.5 mb-3">
              <Equal className="h-4 w-4 text-gray-500" />
              <h4 className="text-sm font-semibold text-gray-900 dark:text-white">
                共有技能 ({diff.common.length})
              </h4>
            </div>
            <div className="flex flex-wrap gap-1.5">
              {diff.common.length === 0 ? (
                <p className="text-xs text-gray-400">无</p>
              ) : (
                diff.common.map((skill) => (
                  <Badge
                    key={skill}
                    variant="secondary"
                    className="text-[11px] bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-300"
                  >
                    {skill}
                  </Badge>
                ))
              )}
            </div>
          </CardContent>
        </Card>

        {/* B 独有 */}
        <Card className="border-red-200 dark:border-red-900">
          <CardContent className="p-4">
            <div className="flex items-center gap-1.5 mb-3">
              <Minus className="h-4 w-4 text-red-600" />
              <h4 className="text-sm font-semibold text-gray-900 dark:text-white">
                版本 B 独有 ({diff.removed.length})
              </h4>
            </div>
            <div className="flex flex-wrap gap-1.5">
              {diff.removed.length === 0 ? (
                <p className="text-xs text-gray-400">无</p>
              ) : (
                diff.removed.map((skill) => (
                  <Badge
                    key={skill}
                    variant="destructive"
                    className="text-[11px] bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300"
                  >
                    {skill}
                  </Badge>
                ))
              )}
            </div>
          </CardContent>
        </Card>
      </div>

      {/* 段落级 diff */}
      {diff.changed.length > 0 && (
        <Card>
          <CardContent className="p-4">
            <h4 className="text-sm font-semibold text-gray-900 dark:text-white mb-3">
              段落内容差异 ({diff.changed.length})
            </h4>
            <div className="space-y-3">
              {diff.changed.map((section, idx) => (
                <div
                  key={idx}
                  className="rounded-lg border border-gray-200 dark:border-gray-700 overflow-hidden"
                >
                  <div className="flex items-center justify-between px-3 py-1.5 bg-gray-50 dark:bg-gray-800/50 border-b border-gray-200 dark:border-gray-700">
                    <span className="text-xs font-medium text-gray-700 dark:text-gray-300">
                      {section.section}
                    </span>
                    <Badge
                      variant={section.changeType === "added" ? "success" : section.changeType === "removed" ? "destructive" : "warning"}
                      className="text-[10px]"
                    >
                      {section.changeType === "added"
                        ? "新增"
                        : section.changeType === "removed"
                        ? "删除"
                        : "修改"}
                    </Badge>
                  </div>
                  <div className="grid grid-cols-1 md:grid-cols-2 divide-x divide-gray-200 dark:divide-gray-700">
                    <div className="p-3">
                      <p className="text-[10px] text-gray-400 mb-1">版本 A</p>
                      <pre className="text-xs text-gray-600 dark:text-gray-400 whitespace-pre-wrap font-sans max-h-40 overflow-y-auto">
                        {section.before || "（空）"}
                      </pre>
                    </div>
                    <div className="p-3">
                      <p className="text-[10px] text-gray-400 mb-1">版本 B</p>
                      <pre className="text-xs text-gray-600 dark:text-gray-400 whitespace-pre-wrap font-sans max-h-40 overflow-y-auto">
                        {section.after || "（空）"}
                      </pre>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
