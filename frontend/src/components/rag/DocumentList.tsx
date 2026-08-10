"use client";

import * as React from "react";
import { Card, CardContent } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { FileText, Trash2, Clock, CheckCircle2, Loader2 } from "lucide-react";
import { useRAG } from "@/hooks/useRAG";
import { formatDistanceToNow } from "date-fns";
import { zhCN } from "date-fns/locale";

const statusConfig = {
  pending: { label: "待处理", variant: "secondary" as const, icon: Clock },
  vectorizing: { label: "向量化中", variant: "warning" as const, icon: Loader2 },
  ready: { label: "已就绪", variant: "success" as const, icon: CheckCircle2 },
  error: { label: "错误", variant: "destructive" as const, icon: Trash2 },
};

export function DocumentList() {
  const { documents, isDocumentsLoading, deleteDocument } = useRAG();

  const formatSize = (bytes: number) => {
    if (!bytes) return "-";
    if (bytes < 1024) return bytes + " B";
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + " KB";
    return (bytes / (1024 * 1024)).toFixed(1) + " MB";
  };

  return (
    <Card>
      <CardContent className="p-6">
        <div className="flex items-center justify-between mb-4">
          <h3 className="font-semibold text-gray-900 dark:text-white">
            我的文档 ({documents.length})
          </h3>
        </div>

        {isDocumentsLoading ? (
          <div className="space-y-2">
            {[1, 2, 3].map((i) => (
              <div
                key={i}
                className="flex items-center gap-3 p-3 rounded-xl border border-gray-200 dark:border-gray-800"
              >
                <div className="h-10 w-10 rounded-xl bg-gray-200 dark:bg-gray-800 animate-pulse" />
                <div className="space-y-2 flex-1">
                  <div className="h-4 w-32 bg-gray-200 dark:bg-gray-800 rounded animate-pulse" />
                  <div className="h-3 w-20 bg-gray-200 dark:bg-gray-800 rounded animate-pulse" />
                </div>
                <div className="h-6 w-16 bg-gray-200 dark:bg-gray-800 rounded-full animate-pulse" />
              </div>
            ))}
          </div>
        ) : documents.length === 0 ? (
          <div className="text-center py-8 text-gray-500 dark:text-gray-400">
            <FileText className="h-12 w-12 mx-auto mb-3 opacity-50" />
            <p>暂无文档，上传你的第一个文档吧</p>
          </div>
        ) : (
          <div className="space-y-2">
            {documents.map((doc: any) => {
              const config = statusConfig[doc.status as keyof typeof statusConfig] || statusConfig.pending;
              const StatusIcon = config.icon;
              return (
                <div
                  key={doc.id}
                  className="flex items-center gap-3 p-3 rounded-xl border border-gray-200 dark:border-gray-800 hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors"
                >
                  <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-brand-100 dark:bg-brand-900/30">
                    <FileText className="h-5 w-5 text-brand-500" />
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-gray-900 dark:text-white truncate">
                      {doc.name}
                    </p>
                    <div className="flex items-center gap-2 text-xs text-gray-500 dark:text-gray-400 mt-0.5">
                      <span>{formatSize(doc.size)}</span>
                      <span>·</span>
                      <span>{doc.chunks || 0} 分块</span>
                      {doc.uploadedAt && (
                        <>
                          <span>·</span>
                          <span>{formatDistanceToNow(new Date(doc.uploadedAt), { addSuffix: true, locale: zhCN })}</span>
                        </>
                      )}
                    </div>
                  </div>
                  <Badge variant={config.variant} className="shrink-0">
                    <StatusIcon className="h-3 w-3 mr-1" />
                    {config.label}
                  </Badge>
                  <button
                    onClick={() => deleteDocument(doc.id)}
                    className="p-2 rounded-lg hover:bg-red-50 dark:hover:bg-red-900/20 text-gray-400 hover:text-red-500 transition-colors shrink-0"
                  >
                    <Trash2 className="h-4 w-4" />
                  </button>
                </div>
              );
            })}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
