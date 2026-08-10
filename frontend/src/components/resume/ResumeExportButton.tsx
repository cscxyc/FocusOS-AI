"use client";

import * as React from "react";
import { Button } from "@/components/ui/Button";
import { downloadResumeExport } from "@/hooks/useResume";
import { FileText, FileDown, Loader2 } from "lucide-react";
import type { ExportFormat } from "@/lib/types";

interface ResumeExportButtonProps {
  versionId: number;
  versionName?: string;
  size?: "sm" | "default" | "lg";
  className?: string;
}

interface DownloadState {
  format: ExportFormat | null;
  loading: boolean;
  error: string | null;
}

/**
 * Sprint 7-C-B: 简历导出按钮组件
 * 支持 PDF / Word / Markdown 三种格式下载
 */
export function ResumeExportButton({
  versionId,
  versionName,
  size = "sm",
  className,
}: ResumeExportButtonProps) {
  const [state, setState] = React.useState<DownloadState>({
    format: null,
    loading: false,
    error: null,
  });

  const handleDownload = React.useCallback(
    async (format: ExportFormat) => {
      setState({ format, loading: true, error: null });
      try {
        const { blob, filename } = await downloadResumeExport(versionId, format);
        const url = URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
        setState({ format: null, loading: false, error: null });
      } catch (e) {
        const msg = e instanceof Error ? e.message : "导出失败";
        setState({ format: null, loading: false, error: msg });
      }
    },
    [versionId]
  );

  const formats: Array<{
    format: ExportFormat;
    label: string;
    icon: typeof FileText;
    color: string;
  }> = [
    { format: "pdf", label: "PDF", icon: FileDown, color: "text-red-600" },
    { format: "docx", label: "Word", icon: FileText, color: "text-blue-600" },
    { format: "md", label: "Markdown", icon: FileText, color: "text-gray-600" },
  ];

  return (
    <div className={`flex flex-wrap gap-2 ${className || ""}`}>
      {formats.map(({ format, label, icon: Icon, color }) => {
        const isLoading = state.loading && state.format === format;
        return (
          <Button
            key={format}
            variant="outline"
            size={size}
            disabled={state.loading}
            onClick={() => handleDownload(format)}
            className="gap-1.5"
            title={`下载 ${label} 格式${versionName ? ` - ${versionName}` : ""}`}
          >
            {isLoading ? (
              <Loader2 className={`h-3.5 w-3.5 animate-spin ${color}`} />
            ) : (
              <Icon className={`h-3.5 w-3.5 ${color}`} />
            )}
            {label}
          </Button>
        );
      })}
      {state.error && (
        <span className="text-xs text-red-600 dark:text-red-400 self-center">
          {state.error}
        </span>
      )}
    </div>
  );
}
