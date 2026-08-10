"use client";

import * as React from "react";
import { Button } from "@/components/ui/Button";
import { ResumePreview } from "./ResumePreview";
import { useUpdateResumeVersion } from "@/hooks/useResume";
import { Save, Loader2, Eye, Pencil, CheckCircle2, AlertCircle } from "lucide-react";
import type { ResumeVersion } from "@/lib/types";

interface ResumeEditorProps {
  version: ResumeVersion;
  onSaved?: (updated: ResumeVersion) => void;
  onCancel?: () => void;
}

/**
 * Sprint 7-C-B: 简历在线编辑器
 * 左侧 Markdown 编辑（textarea）+ 右侧实时预览（react-markdown）
 * 保存调用 PUT /api/resume/versions/{id}
 */
export function ResumeEditor({ version, onSaved, onCancel }: ResumeEditorProps) {
  const [content, setContent] = React.useState(version.content || "");
  const [versionName, setVersionName] = React.useState(version.versionName || "");
  const [showPreview, setShowPreview] = React.useState(true);
  const [savedAt, setSavedAt] = React.useState<number | null>(null);

  const updateMutation = useUpdateResumeVersion();

  // 当 version prop 变化时重置内容
  React.useEffect(() => {
    setContent(version.content || "");
    setVersionName(version.versionName || "");
    setSavedAt(null);
  }, [version.id]);

  const contentChanged = content !== (version.content || "");
  const nameChanged = versionName !== (version.versionName || "");
  const hasChanges = contentChanged || nameChanged;

  const handleSave = async () => {
    if (!hasChanges) return;
    try {
      const updated = await updateMutation.mutateAsync({
        versionId: version.id,
        data: {
          content: contentChanged ? content : undefined,
          versionName: nameChanged ? versionName : undefined,
        },
      });
      setSavedAt(Date.now());
      onSaved?.(updated);
    } catch (e) {
      // mutation isError 已处理
    }
  };

  return (
    <div className="space-y-4">
      {/* 顶部工具栏 */}
      <div className="flex items-center gap-3 flex-wrap p-3 rounded-xl bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-700 shadow-sm">
        <div className="flex items-center gap-2 flex-1 min-w-[200px]">
          <label className="text-xs text-gray-500 dark:text-gray-400 whitespace-nowrap">版本名</label>
          <input
            type="text"
            value={versionName}
            onChange={(e) => setVersionName(e.target.value)}
            className="flex-1 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 py-1.5 text-sm focus:ring-2 focus:ring-brand-500 outline-none"
            placeholder="如：AI应用开发工程师版"
          />
        </div>

        <div className="flex items-center gap-2">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => setShowPreview((v) => !v)}
            className="gap-1.5"
            title={showPreview ? "隐藏预览" : "显示预览"}
          >
            {showPreview ? <Eye className="h-4 w-4" /> : <Pencil className="h-4 w-4" />}
            {showPreview ? "预览中" : "仅编辑"}
          </Button>

          {onCancel && (
            <Button variant="outline" size="sm" onClick={onCancel} disabled={updateMutation.isPending}>
              取消
            </Button>
          )}

          <Button
            size="sm"
            onClick={handleSave}
            disabled={!hasChanges || updateMutation.isPending}
            className="gap-1.5"
          >
            {updateMutation.isPending ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : savedAt && !hasChanges ? (
              <CheckCircle2 className="h-4 w-4 text-green-400" />
            ) : (
              <Save className="h-4 w-4" />
            )}
            {updateMutation.isPending ? "保存中..." : savedAt && !hasChanges ? "已保存" : "保存"}
          </Button>
        </div>
      </div>

      {/* 状态提示 */}
      {updateMutation.isError && (
        <div className="flex items-center gap-2 text-sm text-red-600 dark:text-red-400 px-3 py-2 rounded-lg bg-red-50 dark:bg-red-900/20">
          <AlertCircle className="h-4 w-4" />
          {updateMutation.error instanceof Error
            ? updateMutation.error.message
            : "保存失败"}
        </div>
      )}
      {hasChanges && (
        <div className="text-xs text-amber-600 dark:text-amber-400 px-1">
          有未保存的修改
        </div>
      )}

      {/* 编辑器主体：左右分栏 */}
      <div className={`grid gap-4 ${showPreview ? "lg:grid-cols-2" : "grid-cols-1"}`}>
        {/* 左侧编辑区 */}
        <div className="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 overflow-hidden">
          <div className="flex items-center justify-between px-3 py-2 border-b border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800/50">
            <span className="text-xs font-medium text-gray-500 dark:text-gray-400 flex items-center gap-1.5">
              <Pencil className="h-3 w-3" /> Markdown 编辑
            </span>
            <span className="text-[10px] text-gray-400">{content.length} 字符</span>
          </div>
          <textarea
            value={content}
            onChange={(e) => setContent(e.target.value)}
            className="w-full h-[600px] p-4 text-sm font-mono leading-relaxed bg-white dark:bg-gray-900 text-gray-800 dark:text-gray-200 outline-none resize-none focus:ring-2 focus:ring-brand-500"
            placeholder="# Your Name&#10;&#10;## 教育背景&#10;...&#10;## 项目经历&#10;..."
            spellCheck={false}
          />
        </div>

        {/* 右侧预览区 */}
        {showPreview && (
          <div className="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 overflow-hidden">
            <div className="flex items-center justify-between px-3 py-2 border-b border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800/50">
              <span className="text-xs font-medium text-gray-500 dark:text-gray-400 flex items-center gap-1.5">
                <Eye className="h-3 w-3" /> 实时预览
              </span>
            </div>
            <div className="h-[600px] overflow-y-auto p-6">
              <ResumePreview content={content} />
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
