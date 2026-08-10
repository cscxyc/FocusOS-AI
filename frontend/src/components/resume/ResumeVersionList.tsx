"use client";

import * as React from "react";
import { Card, CardContent } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import {
  FileText,
  Eye,
  Pencil,
  Download,
  GitCompare,
  Trash2,
  CheckCircle2,
  Loader2,
  Calendar,
  Briefcase,
  Plus,
  AlertCircle,
  Gauge,
} from "lucide-react";
import type { ResumeVersion } from "@/lib/types";

interface ResumeVersionListProps {
  versions: ResumeVersion[];
  isLoading: boolean;
  isError: boolean;
  error?: unknown;
  selectedIds: number[];
  onSelect: (id: number) => void;
  onView: (version: ResumeVersion) => void;
  onEdit: (version: ResumeVersion) => void;
  onExport: (version: ResumeVersion) => void;
  onDiff: () => void;
  onDelete: (version: ResumeVersion) => void;
  onActivate: (version: ResumeVersion) => void;
  onCreateNew: () => void;
  /** Sprint 8-A: AI 评分回调 */
  onEvaluate?: (version: ResumeVersion) => void;
  /** Sprint 8-A: 正在评分的版本 ID（用于按钮 loading 状态） */
  evaluatingVersionId?: number | null;
}

/**
 * Sprint 7-C-B: 简历版本列表组件
 * 卡片网格展示所有版本，支持选择/查看/编辑/导出/对比/删除/激活
 */
export function ResumeVersionList({
  versions,
  isLoading,
  isError,
  error,
  selectedIds,
  onSelect,
  onView,
  onEdit,
  onExport,
  onDiff,
  onDelete,
  onActivate,
  onCreateNew,
  onEvaluate,
  evaluatingVersionId,
}: ResumeVersionListProps) {
  const formatDate = (s: string) => {
    try {
      const d = new Date(s);
      return d.toLocaleString("zh-CN", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
      });
    } catch {
      return s;
    }
  };

  if (isLoading) {
    return (
      <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
        {[1, 2, 3].map((i) => (
          <Card key={i}>
            <CardContent className="p-5 space-y-3">
              <div className="h-5 w-32 bg-gray-200 dark:bg-gray-800 rounded animate-pulse" />
              <div className="h-4 w-20 bg-gray-200 dark:bg-gray-800 rounded animate-pulse" />
              <div className="h-4 w-24 bg-gray-200 dark:bg-gray-800 rounded animate-pulse" />
              <div className="flex gap-2 pt-2">
                <div className="h-8 w-16 bg-gray-200 dark:bg-gray-800 rounded animate-pulse" />
                <div className="h-8 w-16 bg-gray-200 dark:bg-gray-800 rounded animate-pulse" />
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
    );
  }

  if (isError) {
    return (
      <Card>
        <CardContent className="p-8 text-center">
          <AlertCircle className="h-12 w-12 mx-auto mb-3 text-red-500" />
          <p className="text-sm text-red-600 dark:text-red-400 mb-2">加载简历版本失败</p>
          <p className="text-xs text-gray-500">
            {error instanceof Error ? error.message : "未知错误"}
          </p>
        </CardContent>
      </Card>
    );
  }

  if (versions.length === 0) {
    return (
      <Card>
        <CardContent className="p-12 text-center">
          <FileText className="h-16 w-16 mx-auto mb-4 text-gray-300 dark:text-gray-600" />
          <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-2">
            暂无简历版本
          </h3>
          <p className="text-sm text-gray-500 dark:text-gray-400 mb-6">
            通过 Career Workflow 分析岗位后，可保存简历版本到此处管理
          </p>
          <Button onClick={onCreateNew} className="gap-1.5">
            <Plus className="h-4 w-4" />
            手动创建简历版本
          </Button>
        </CardContent>
      </Card>
    );
  }

  return (
    <div className="space-y-4">
      {/* 顶部操作栏 */}
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div className="text-sm text-gray-500 dark:text-gray-400">
          共 <span className="font-semibold text-gray-900 dark:text-white">{versions.length}</span> 个版本
          {selectedIds.length > 0 && (
            <span className="ml-3 text-brand-600 dark:text-brand-400">
              已选 {selectedIds.length} 个{selectedIds.length === 2 ? "（可对比）" : ""}
            </span>
          )}
        </div>
        <div className="flex gap-2">
          {selectedIds.length === 2 && (
            <Button size="sm" onClick={onDiff} className="gap-1.5">
              <GitCompare className="h-4 w-4" />
              对比选中版本
            </Button>
          )}
          <Button size="sm" variant="outline" onClick={onCreateNew} className="gap-1.5">
            <Plus className="h-4 w-4" />
            新建版本
          </Button>
        </div>
      </div>

      {/* 卡片网格 */}
      <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
        {versions.map((v) => {
          const isSelected = selectedIds.includes(v.id);
          return (
            <Card
              key={v.id}
              className={`relative transition-all cursor-pointer ${
                isSelected
                  ? "ring-2 ring-brand-500 shadow-lg"
                  : v.isActive
                  ? "ring-1 ring-green-400"
                  : "hover:shadow-md"
              }`}
            >
              <CardContent className="p-5 space-y-3">
                {/* 头部：岗位 + 激活标记 */}
                <div className="flex items-start justify-between gap-2">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-1.5 mb-1">
                      <Briefcase className="h-3.5 w-3.5 text-brand-500 flex-shrink-0" />
                      <span className="text-xs text-gray-500 dark:text-gray-400">目标岗位</span>
                    </div>
                    <h3 className="font-semibold text-gray-900 dark:text-white truncate">
                      {v.targetPosition || "未指定岗位"}
                    </h3>
                  </div>
                  {v.isActive && (
                    <Badge variant="success" className="flex-shrink-0 gap-1">
                      <CheckCircle2 className="h-3 w-3" />
                      当前使用
                    </Badge>
                  )}
                </div>

                {/* 版本名 */}
                <div>
                  <p className="text-sm font-medium text-gray-700 dark:text-gray-300 truncate">
                    {v.versionName || `版本 #${v.id}`}
                  </p>
                </div>

                {/* 元信息 */}
                <div className="flex items-center gap-3 text-[11px] text-gray-400 dark:text-gray-500">
                  <span className="flex items-center gap-1">
                    <Calendar className="h-3 w-3" />
                    {formatDate(v.createdAt)}
                  </span>
                  {v.sourceReportId && (
                    <span className="text-gray-400">报告 #{v.sourceReportId}</span>
                  )}
                </div>

                {/* 操作按钮 */}
                <div className="flex items-center gap-1.5 pt-2 border-t border-gray-100 dark:border-gray-800 flex-wrap">
                  <Button
                    size="sm"
                    variant="ghost"
                    onClick={() => onView(v)}
                    className="gap-1 h-7 px-2"
                  >
                    <Eye className="h-3.5 w-3.5" />
                    查看
                  </Button>
                  <Button
                    size="sm"
                    variant="ghost"
                    onClick={() => onEdit(v)}
                    className="gap-1 h-7 px-2"
                  >
                    <Pencil className="h-3.5 w-3.5" />
                    编辑
                  </Button>
                  <Button
                    size="sm"
                    variant="ghost"
                    onClick={() => onExport(v)}
                    className="gap-1 h-7 px-2"
                  >
                    <Download className="h-3.5 w-3.5" />
                    导出
                  </Button>
                  {onEvaluate && (
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => onEvaluate(v)}
                      disabled={evaluatingVersionId === v.id}
                      className="gap-1 h-7 px-2 text-brand-600 dark:text-brand-400"
                    >
                      {evaluatingVersionId === v.id ? (
                        <Loader2 className="h-3.5 w-3.5 animate-spin" />
                      ) : (
                        <Gauge className="h-3.5 w-3.5" />
                      )}
                      AI评分
                    </Button>
                  )}
                  {!v.isActive && (
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => onActivate(v)}
                      className="gap-1 h-7 px-2 text-green-600 dark:text-green-400"
                    >
                      <CheckCircle2 className="h-3.5 w-3.5" />
                      激活
                    </Button>
                  )}
                  <Button
                    size="sm"
                    variant="ghost"
                    onClick={() => onDelete(v)}
                    className="gap-1 h-7 px-2 ml-auto text-red-500 hover:text-red-600"
                  >
                    <Trash2 className="h-3.5 w-3.5" />
                  </Button>
                </div>

                {/* 选择对比复选框 */}
                <label
                  className="absolute top-3 right-3 flex items-center gap-1.5 cursor-pointer text-[10px] text-gray-400 hover:text-brand-500"
                  onClick={(e) => e.stopPropagation()}
                >
                  <input
                    type="checkbox"
                    checked={isSelected}
                    onChange={() => onSelect(v.id)}
                    className="h-3.5 w-3.5 rounded border-gray-300 text-brand-500 focus:ring-brand-500 cursor-pointer"
                  />
                  对比
                </label>
              </CardContent>
            </Card>
          );
        })}
      </div>
    </div>
  );
}
