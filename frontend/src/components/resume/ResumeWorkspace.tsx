"use client";

import * as React from "react";
import { Card, CardContent } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import {
  useResumeVersions,
  useResumeVersion,
  useDeleteResumeVersion,
  useActivateResumeVersion,
  useEvaluateResume,
} from "@/hooks/useResume";
import { ResumeVersionList } from "./ResumeVersionList";
import { ResumePreview } from "./ResumePreview";
import { ResumeEditor } from "./ResumeEditor";
import { ResumeExportButton } from "./ResumeExportButton";
import { ResumeDiff } from "./ResumeDiff";
import { ResumeEvaluation } from "./ResumeEvaluation";
import {
  ArrowLeft,
  Pencil,
  Download,
  AlertCircle,
  Loader2,
  Sparkles,
  X,
  FileText,
  Gauge,
} from "lucide-react";
import type { ResumeVersion, EvaluateResumeResponse } from "@/lib/types";

type ViewMode = "list" | "view" | "edit" | "diff" | "evaluate";

interface DiffTarget {
  versionAId: number;
  versionBId: number;
}

/**
 * Sprint 7-C-B: Resume Workspace 主容器
 * 管理列表/查看/编辑/对比四种视图模式
 */
export function ResumeWorkspace() {
  const [mode, setMode] = React.useState<ViewMode>("list");
  const [selectedVersionId, setSelectedVersionId] = React.useState<number | null>(null);
  const [selectedIds, setSelectedIds] = React.useState<number[]>([]);
  const [diffTarget, setDiffTarget] = React.useState<DiffTarget | null>(null);
  const [showExportPanel, setShowExportPanel] = React.useState(false);
  const [evaluationResult, setEvaluationResult] = React.useState<EvaluateResumeResponse | null>(null);

  const versionsQuery = useResumeVersions();
  const detailQuery = useResumeVersion(mode === "view" || mode === "edit" || mode === "evaluate" ? selectedVersionId : null);
  const deleteMutation = useDeleteResumeVersion();
  const activateMutation = useActivateResumeVersion();
  const evaluateMutation = useEvaluateResume();

  const handleSelect = (id: number) => {
    setSelectedIds((prev) => {
      if (prev.includes(id)) {
        return prev.filter((x) => x !== id);
      }
      if (prev.length >= 2) {
        return [prev[1], id]; // 保留后一个，替换第一个
      }
      return [...prev, id];
    });
  };

  const handleView = (v: ResumeVersion) => {
    setSelectedVersionId(v.id);
    setMode("view");
  };

  const handleEdit = (v: ResumeVersion) => {
    setSelectedVersionId(v.id);
    setMode("edit");
  };

  const handleExport = (v: ResumeVersion) => {
    setSelectedVersionId(v.id);
    setShowExportPanel(true);
    setMode("view");
  };

  const handleDelete = async (v: ResumeVersion) => {
    if (!confirm(`确认删除版本「${v.versionName || `#${v.id}`}」？此操作不可恢复。`)) return;
    try {
      await deleteMutation.mutateAsync(v.id);
      setSelectedIds((prev) => prev.filter((x) => x !== v.id));
    } catch (e) {
      alert(e instanceof Error ? e.message : "删除失败");
    }
  };

  const handleActivate = async (v: ResumeVersion) => {
    try {
      await activateMutation.mutateAsync(v.id);
    } catch (e) {
      alert(e instanceof Error ? e.message : "激活失败");
    }
  };

  const handleDiff = () => {
    if (selectedIds.length !== 2) return;
    setDiffTarget({ versionAId: selectedIds[0], versionBId: selectedIds[1] });
    setMode("diff");
  };

  // Sprint 8-A: AI 评分
  const handleEvaluate = async (v: ResumeVersion) => {
    setSelectedVersionId(v.id);
    setEvaluationResult(null);
    setMode("evaluate");
    try {
      // 优先使用 sourceReportId（关联的 CareerAnalysisReport）作为 JD 来源
      const result = await evaluateMutation.mutateAsync({
        resumeVersionId: v.id,
        careerReportId: v.sourceReportId ?? undefined,
      });
      setEvaluationResult(result);
    } catch (e) {
      alert(e instanceof Error ? e.message : "AI 评分失败");
    }
  };

  const handleCreateNew = () => {
    // 跳转到 Career Workflow 标签（由父组件处理）或打开手动创建弹窗
    // 这里通过事件通知父组件
    alert("请在「AI Career 分析」标签中运行工作流，完成后可保存为新简历版本。\n或使用「简历优化」标签生成优化建议。");
  };

  // ====== 视图切换 ======
  if (mode === "diff" && diffTarget) {
    return (
      <div className="space-y-4">
        <Button
          variant="ghost"
          size="sm"
          onClick={() => {
            setMode("list");
            setDiffTarget(null);
          }}
          className="gap-1.5"
        >
          <ArrowLeft className="h-4 w-4" />
          返回版本列表
        </Button>
        <ResumeDiff
          versionAId={diffTarget.versionAId}
          versionBId={diffTarget.versionBId}
          onClose={() => {
            setMode("list");
            setDiffTarget(null);
          }}
        />
      </div>
    );
  }

  if (mode === "view" && selectedVersionId) {
    return (
      <div className="space-y-4">
        <div className="flex items-center justify-between flex-wrap gap-3">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => {
              setMode("list");
              setSelectedVersionId(null);
              setShowExportPanel(false);
            }}
            className="gap-1.5"
          >
            <ArrowLeft className="h-4 w-4" />
            返回版本列表
          </Button>
          <div className="flex gap-2">
            <Button
              size="sm"
              variant="outline"
              onClick={() => setMode("edit")}
              className="gap-1.5"
            >
              <Pencil className="h-4 w-4" />
              编辑
            </Button>
            <Button
              size="sm"
              variant="outline"
              onClick={() => setShowExportPanel((v) => !v)}
              className="gap-1.5"
            >
              <Download className="h-4 w-4" />
              导出
            </Button>
          </div>
        </div>

        {/* 导出面板 */}
        {showExportPanel && detailQuery.data && (
          <Card>
            <CardContent className="p-4">
              <div className="flex items-center justify-between mb-3">
                <h4 className="text-sm font-semibold text-gray-900 dark:text-white">
                  导出简历
                </h4>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => setShowExportPanel(false)}
                  className="h-7 w-7 p-0"
                >
                  <X className="h-4 w-4" />
                </Button>
              </div>
              <ResumeExportButton
                versionId={detailQuery.data.id}
                versionName={detailQuery.data.versionName}
                size="default"
              />
            </CardContent>
          </Card>
        )}

        {/* 简历预览 */}
        <Card>
          <CardContent className="p-6">
            {detailQuery.isLoading ? (
              <div className="flex items-center justify-center py-16">
                <Loader2 className="h-8 w-8 animate-spin text-brand-500" />
              </div>
            ) : detailQuery.isError ? (
              <div className="text-center py-16">
                <AlertCircle className="h-12 w-12 mx-auto mb-3 text-red-500" />
                <p className="text-sm text-red-600">
                  {detailQuery.error instanceof Error
                    ? detailQuery.error.message
                    : "加载失败"}
                </p>
              </div>
            ) : detailQuery.data ? (
              <>
                <div className="flex items-center gap-2 mb-4 pb-4 border-b border-gray-200 dark:border-gray-700">
                  <FileText className="h-5 w-5 text-brand-500" />
                  <h3 className="font-semibold text-gray-900 dark:text-white">
                    {detailQuery.data.versionName || `版本 #${detailQuery.data.id}`}
                  </h3>
                  <Badge variant="secondary" className="ml-1">
                    {detailQuery.data.targetPosition || "未指定岗位"}
                  </Badge>
                  {detailQuery.data.isActive && (
                    <Badge variant="success" className="gap-1">
                      当前使用
                    </Badge>
                  )}
                </div>
                <ResumePreview content={detailQuery.data.content || ""} />
              </>
            ) : null}
          </CardContent>
        </Card>
      </div>
    );
  }

  if (mode === "edit" && selectedVersionId) {
    return (
      <div className="space-y-4">
        <Button
          variant="ghost"
          size="sm"
          onClick={() => setMode("view")}
          className="gap-1.5"
        >
          <ArrowLeft className="h-4 w-4" />
          返回查看
        </Button>

        {detailQuery.isLoading ? (
          <Card>
            <CardContent className="p-8 text-center">
              <Loader2 className="h-8 w-8 mx-auto animate-spin text-brand-500" />
            </CardContent>
          </Card>
        ) : detailQuery.data ? (
          <ResumeEditor
            version={detailQuery.data}
            onSaved={() => {
              // 保存成功后留在编辑模式，detailQuery 会自动 invalidate
            }}
            onCancel={() => setMode("view")}
          />
        ) : null}
      </div>
    );
  }

  // Sprint 8-A: AI 评分视图
  if (mode === "evaluate" && selectedVersionId) {
    return (
      <div className="space-y-4">
        <div className="flex items-center justify-between flex-wrap gap-3">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => {
              setMode("list");
              setSelectedVersionId(null);
              setEvaluationResult(null);
            }}
            className="gap-1.5"
          >
            <ArrowLeft className="h-4 w-4" />
            返回版本列表
          </Button>
          {detailQuery.data && (
            <Badge variant="secondary" className="gap-1">
              <FileText className="h-3 w-3" />
              {detailQuery.data.versionName || `版本 #${detailQuery.data.id}`}
            </Badge>
          )}
        </div>

        {evaluateMutation.isPending && !evaluationResult ? (
          <Card>
            <CardContent className="p-12 text-center">
              <Loader2 className="h-10 w-10 mx-auto animate-spin text-brand-500 mb-4" />
              <h3 className="text-sm font-semibold text-gray-900 dark:text-white mb-1">
                AI 正在评估简历质量...
              </h3>
              <p className="text-xs text-gray-500 dark:text-gray-400">
                基于 JD 匹配度、ATS 关键词、STAR 经历、项目深度、完整度五维评分
              </p>
            </CardContent>
          </Card>
        ) : evaluationResult ? (
          <ResumeEvaluation
            evaluation={evaluationResult.evaluation}
            jobTitle={evaluationResult.jobTitle}
            company={evaluationResult.company}
          />
        ) : evaluateMutation.isError ? (
          <Card>
            <CardContent className="p-8 text-center">
              <AlertCircle className="h-12 w-12 mx-auto mb-3 text-red-500" />
              <p className="text-sm text-red-600 dark:text-red-400 mb-2">AI 评分失败</p>
              <p className="text-xs text-gray-500 mb-4">
                {evaluateMutation.error instanceof Error
                  ? evaluateMutation.error.message
                  : "未知错误"}
              </p>
              <Button
                size="sm"
                variant="outline"
                onClick={() => {
                  if (detailQuery.data) handleEvaluate(detailQuery.data);
                }}
                className="gap-1.5"
              >
                <Gauge className="h-4 w-4" />
                重新评分
              </Button>
            </CardContent>
          </Card>
        ) : null}
      </div>
    );
  }

  // 默认：列表视图
  return (
    <div className="space-y-4">
      {/* 头部说明 */}
      <Card>
        <CardContent className="p-4">
          <div className="flex items-center gap-3">
            <div className="h-10 w-10 rounded-xl bg-gradient-to-br from-brand-500 to-accent-500 flex items-center justify-center flex-shrink-0">
              <Sparkles className="h-5 w-5 text-white" />
            </div>
            <div className="flex-1">
              <h2 className="font-semibold text-gray-900 dark:text-white">
                Resume Workspace
              </h2>
              <p className="text-xs text-gray-500 dark:text-gray-400">
                管理你的 AI 生成简历版本，支持在线编辑、多格式导出、版本对比、AI 质量评分
              </p>
            </div>
          </div>
        </CardContent>
      </Card>

      <ResumeVersionList
        versions={versionsQuery.data || []}
        isLoading={versionsQuery.isLoading}
        isError={versionsQuery.isError}
        error={versionsQuery.error}
        selectedIds={selectedIds}
        onSelect={handleSelect}
        onView={handleView}
        onEdit={handleEdit}
        onExport={handleExport}
        onDiff={handleDiff}
        onDelete={handleDelete}
        onActivate={handleActivate}
        onCreateNew={handleCreateNew}
        onEvaluate={handleEvaluate}
        evaluatingVersionId={evaluateMutation.isPending ? selectedVersionId : null}
      />
    </div>
  );
}
